package fr.uge.ugegreed.calc;


import static java.nio.file.StandardOpenOption.CREATE;
import static java.nio.file.StandardOpenOption.WRITE;
import static java.nio.file.StandardOpenOption.TRUNCATE_EXISTING;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.channels.SelectionKey;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Objects;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.logging.Logger;
import java.util.stream.Collectors;

import fr.uge.ugegreed.Checker;
import fr.uge.ugegreed.Client;
import fr.uge.ugegreed.ClientServer;
import fr.uge.ugegreed.Context;
import fr.uge.ugegreed.Mode;
import fr.uge.ugegreed.query.*;

public class CalcExecutor {

  /**
  *
  * @param fileName {@link String} : fichier de sortie
  * @param res {@link ArrayList} de String : liste des resultats
  * @param size {@link Integer} : la taille de la liste
  */
  public record CalcRes(String fileName, HashMap<Integer, String> resLst, int size) {}
  /**
   * @param url {@link String} url
   * @param className {@link String} chemin de la source
   */
  public record Source(String url, String className) {}
  
  private final int poolSize;
  private final ExecutorService executorService;
  private final HashMap<CalcId, CalcRes> personalCalcs = new HashMap<>();
  public final HashMap<CalcId, Source> source = new HashMap<>();
  private static final Logger logger = Logger.getLogger(ClientServer.class.getName());
  private final Object localLock = new Object();
  private final ClientServer clientServer;
  private static final Charset UTF8 = Charset.forName("UTF8");
  
  
  public CalcExecutor(int poolSize, ClientServer clientServer) {
    this.poolSize = poolSize;
    this.executorService = Executors.newFixedThreadPool(this.poolSize);
    this.clientServer = Objects.requireNonNull(clientServer);
  }
  
  private int diskOrHTTP(String url) {
	  if(url.length() < 5) {
		  return 1;
	  }
	  var tmp = url.substring(0, 4);
	  if(tmp.equals("http")) {
		  return 0;
	  }
	  return 1;
  }
  
  /**
   * utilise la fonction checker selon les parametres<br>
   * retourne null si il n'y arrive pas sinon un {@link String}
   * @param url {@link String} de la location du checker 
   * @param className {@link String} du nom du checker
   * @param testValue {@link Integer} de la valeur à checker
   * @return le resultat de la fonction check
   */
  private String doCheck(String url, String className, int testValue) {
	try {
		Object tmp = null; 
		if(diskOrHTTP(url) == 0) {
			tmp = Client.checkerFromHTTP(url, className).orElseThrow();
		}
		else {
			tmp = Client.checkerFromDisk(Path.of(url), className).orElseThrow();
		}
		var checker = (Checker) tmp;
		return checker.check(testValue);
	} catch (InterruptedException e) {
	  logger.warning("Impossible to check " + className + " at the location" + url);
	}
	return null;
  }
  
  /**
   * ajoute un calcul à l'executor
   * @param calcId
   * @param calcRes
   */
  public void addCalc(CalcId calcId, CalcRes calcRes) {
    synchronized (localLock) {
      Objects.requireNonNull(calcId);
      Objects.requireNonNull(calcRes);
      personalCalcs.put(calcId, calcRes);
    }
  }
  
  /**
   * ajoute un resultat au calcId
   * @param calcId
   * @param stringRes
   */
  public void addRes(CalcId calcId, String stringRes, int value) {
    Objects.requireNonNull(calcId);
    synchronized (localLock) {
      var calcRes = personalCalcs.get(calcId);
      var lines = calcRes.resLst();
      lines.put(value,stringRes);
    }
  }
  
  /**
   * regarde si un calcul est terminé
   * @param calcId Le {@link CalcId} à vérifier
   * @return un {@link Boolean}
   */
  public boolean calcIsFinished(CalcId calcId) {
    synchronized(localLock) {
      var calcRes = personalCalcs.get(calcId);
      return calcRes.resLst().size() == calcRes.size();
    }
  }
  
  /**
   * Permet de récuperer le check via l'executor
   * 
   * @param url
   * @param className
   * @param value
   * @return le check qui est un {@link String}
   */
  public String getCheck(String url, String className, int value) {
    var future = executorService.submit(() -> doCheck(url, className, value));
    try {
      return (String) future.get();
    } catch (InterruptedException e) {
      e.printStackTrace();
    } catch (ExecutionException e) {
      e.printStackTrace();
    }
    return null;
  };
  
  /**
   * 
   * @param calcId
   * @return un {@link String} du résultat final
   */
  public String getFinalRes(CalcId calcId) {
    if (!calcIsFinished(calcId)) {
      logger.warning("Wait ! the calcul is not finished yet !");
      return null;
    }
    var builder = new StringBuilder();
    var calcRes = personalCalcs.get(calcId);
    var separator = "";
    for (var resInt: calcRes.resLst().keySet()) {
      String resString = calcRes.resLst.get(resInt);
      builder.append(separator).append(resString);
      separator = "\n";
    }
    return builder.toString();
  };
  
  /**
   * @param calcId
   * @return {@link CalcRes} de la hashMap personelle de calcId
   */
  public CalcRes getCalcRes(CalcId calcId) {
    return personalCalcs.get(calcId);
  }
  
  /**
   * stop la possibilité de faire des calculs
   */
  public void shutdown() {
    executorService.shutdown();
  }
  
  /**
   * affiche ce qui n'est pas fini
   */
  private void whatIsNotFinished() {
    for (var cid : personalCalcs.keySet()) {
      System.out.println("cid : " + cid);
      var res = personalCalcs.get(cid);
      if (res.size > res.resLst.size()) {
        System.out.println("Il manque : " + (res.size - res.resLst.size()) +  " resultat(s)");
        for (var i = 0; i < res.size; i++) {
          if (!res.resLst.containsKey(i)) {
            System.out.println("Il manque la valeur : " + i);
          }
        }
      }
      System.out.println(getNotFinishedCalc(cid));
    }
    System.out.println(getAllNotFinishedCalc());
  }
  
  /**
   * 
   * @param calcId {@link CalcId}
   * @return une {@link ArrayList} des valeurs non calculé du calcId
   */
  public ArrayList<Integer> getNotFinishedCalc(CalcId calcId) {
    var notFinish = new ArrayList<Integer>();
    var res = personalCalcs.get(calcId);
    if (res.size > res.resLst.size()) {
      for (var i = 0; i < res.size; i++) {
        if (!res.resLst.containsKey(i)) {
          notFinish.add(i);
        }
      }
    }
    return notFinish;
  }
  /**
   * 
   * @return une {@link HashMap} de tout les les {@link ArrayList} des valeurs non calculé en fonction d'un {@link CalcId}
   */
  public HashMap<CalcId, ArrayList<Integer>> getAllNotFinishedCalc(){
    var notFinish = new HashMap<CalcId, ArrayList<Integer>>();
    for (var cid : personalCalcs.keySet()) {
      var lstInt = getNotFinishedCalc(cid);
      if (!lstInt.equals(new ArrayList<Integer>())) {
        notFinish.put(cid, lstInt);
      }
    }
    return notFinish;
  }
  
  private void execCalc(Query query, int start, int end) {
	  var queryf = (Query0) query;
      if(end > queryf.getEndRange()) {
    	  return;
      }
      System.out.println("Start calc : " + start + " to " + end);
      var calcId = queryf.getCalcId();
      
      for (int i = start; i < end; i++) {
        //calcul du check
        var checkRes = getCheck(queryf.getUrl(), queryf.getClassName(), i);
        //ajout au noeud local ou envoie du resultat
        if (calcId.origin().equals(clientServer.getID())) {
          addRes(calcId, checkRes,i);
        } else {
          sendRes(calcId, checkRes, i);
        }
      }
      writeRes(calcId);
      clientServer.getSemaphore().release();
  }
  
  /**
   * process le calcul d'une query0<br>
   * puis ajoute le resultat à l'executor ou l'envoie au noeud à l'origine du calcul
   * @param query
   * @param start
   * @param end
   * @throws InterruptedException
   */
  public void processCalc(Query query, int start, int end) throws InterruptedException {
    if (clientServer.getSemaphore().availablePermits() == 0 ) {
      //renvoyer false pour signaler que ça va pas
      return;
    }
    clientServer.getSemaphore().acquire();
    executorService.submit(() -> execCalc(query, start,end));
    
  }
  
  /**
   * envoie (grâce au rooter et à l'origine du calcId) le resultat d'une seule valeur d'un calcul
   * @param calcID
   * @param res
   * @param value
   */
  public void sendRes(CalcId calcID, String res, int value) {
    var origin = calcID.origin();
    var dest = clientServer.getRooter().get(origin).getId();
    //System.out.println("Envoi du calcul vers " + origin + " en passant par " + dest);
    var query2 = new Query2(calcID.id(), calcID.origin(), value, res);
    // envoie au potentiel père
    if (clientServer.getMode() == Mode.NORMAL && clientServer.getContext().getId().equals(dest)) {
      System.out.println("Envoi en cours au père : " + value);
      clientServer.getContext().addNewQuery(query2);
      clientServer.getClientSelector().wakeup();
      return;

    }

    // envoie aux fils
    for (var key : clientServer.getServerSelector().keys()) {
      var context = (Context) key.attachment();
      if (context != null && key.isValid() && context.getId().equals(dest)) {
        System.out.println("Envoi en cours à un des fils : " + value);
        context.addNewQuery(query2);
        clientServer.getServerSelector().wakeup();
        return;
      }
    }
  }
  
  /**
   * Si le calcul est complet alors ecrit la somme des resultats dans le fichier de sorti associé
   * @param calcID {@link CalcId} calcul en question
   * @param stringRes {@link String} d'une partie du calcul
   */
  public void writeRes(CalcId calcID) {
    if (calcIsFinished(calcID)) {
      var calcRes = getCalcRes(calcID);
      var lines = calcRes.resLst().values().stream().collect(Collectors.toList());
      
      var fileName = calcRes.fileName();
      try {
        Files.write(Path.of(clientServer.getOutFolder() +"/"+ fileName), lines, UTF8, CREATE, WRITE, TRUNCATE_EXISTING );
        logger.info("The result of "+ getCalcRes(calcID).fileName() +" has been written in the directory : " + clientServer.getOutFolder());
      } catch (IOException e) {
        logger.warning("The result can't be write in the outfilename :" + clientServer.getOutFolder() + "/" + fileName);
      }
    } 
  }

  /**
   * Partage les calculs dans le reseau<br>
   * Puis traite sa plage de calcul<br>
   * Si il est tout seul alors ou qu'il n'y a qu'une seule plage à traiter il process le calcul
   * @param query {@link Query0} demande de calcul
   * @param key {@link SelectionKey} de celui qui a demandé
   * @throws InterruptedException
   */
  public void shareCalcs(Query0 query, SelectionKey key) throws InterruptedException {
    int origin = query.getCalcId().origin().equals(clientServer.getID()) ? 0 : 1;
    int father = clientServer.getMode() == Mode.NORMAL ? 1 : 0;
    int connectedNodes = clientServer.getNbChildren() + father - origin;
    int start = query.getStartRange();
    int end = query.getEndRange();
    System.out.println("Share calc : " + start + " to " + end);
    int total = end - start;
    //si il y a qu'une seule node ou qu'une seule plage à traiter
    if (connectedNodes == 0 || total == 1) {
      processCalc(query, start, end);
    }
    else {
      int step = total / (connectedNodes + 1) + 1;
      System.out.println("step = " + step);
      end = start + step;
      processCalc(query, start, end);
      start = end;
      end += step;
      // Partage des taches au potentiel père
      if (clientServer.getMode() == Mode.NORMAL && !clientServer.getContext().getKey().equals(key)) {
        end = end > query.getEndRange() ? query.getEndRange() : end;
        var query0 = new Query0(query.getCalcId().id(), query.getCalcId().origin(), query.getUrl(),
            query.getClassName(), start, end);
        clientServer.getContext().addNewQuery(query0);
        start = end;
        end += step;
        clientServer.getClientSelector().wakeup();
      }
      // Partage des taches aux fils
      for (var newKey : clientServer.getServerSelector().keys()) {
        var context = (Context) newKey.attachment();
        if (context != null && !newKey.equals(key) && newKey.isValid()) {
          end = end > query.getEndRange() ? query.getEndRange() : end;
          var query0 = new Query0(query.getCalcId().id(), query.getCalcId().origin(), query.getUrl(),
              query.getClassName(), start, end);
          context.addNewQuery(query0);
          clientServer.getServerSelector().wakeup();
          if(end == query.getEndRange()) {
        	  return;
          }
          start = end;
          end += step;
        }
      }

    }
  }
  
  public static void main(String[] args) throws InterruptedException, IOException {
    
	var clientServer = new ClientServer(7777, "../test");
    var executor = new CalcExecutor(5, clientServer);
    
    var calc1 = new CalcId(0, new InetSocketAddress(8888));
    var calc2 = new CalcId(2, new InetSocketAddress(8888));
    executor.addCalc(calc1, new CalcRes("out1", new HashMap<Integer, String>(), 3));
    executor.addCalc(calc2, new CalcRes("out2", new HashMap<Integer, String>(), 3));
    
    executor.addRes(calc1, executor.getCheck("./JarTest/SlowChecker.jar", "fr.uge.slow.SlowChecker", 0), 0);
    executor.addRes(calc2, executor.getCheck("./JarTest/SlowChecker.jar", "fr.uge.slow.SlowChecker", 1), 1);
    executor.addRes(calc2, executor.getCheck("./JarTest/SlowChecker.jar", "fr.uge.slow.SlowChecker", 2), 2);
    
    executor.shutdown();
    executor.whatIsNotFinished();
    
  }
}
