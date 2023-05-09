package fr.uge.ugegreed;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.ConnectException;
import java.net.InetSocketAddress;
import java.nio.channels.Channel;
import java.nio.channels.ClosedSelectorException;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Objects;
import java.util.Scanner;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Semaphore;
import java.util.logging.Level;
import java.util.logging.Logger;

import fr.uge.ugegreed.calc.CalcExecutor;
import fr.uge.ugegreed.calc.CalcExecutor.CalcRes;
import fr.uge.ugegreed.calc.CalcExecutor.Source;
import fr.uge.ugegreed.calc.CalcId;
import fr.uge.ugegreed.query.*;

public class ClientServer {

  private enum AcceptCo{
    POSSIBLE,
    NOT_POSSIBLE,
    WAITING,
  }
  
  private Mode mode;
  private String outFolder;
  private final Object consoleLock = new Object();
  private static final Logger logger = Logger.getLogger(ClientServer.class.getName());
  private Thread console;
  private Thread commands;
  private final BlockingQueue<String> consoleQueue = new ArrayBlockingQueue<>(10);
  private Rooter rooter;
  private int nextCalcIdCreated = 0; //nombre de calculs en cours
  private int nbCalculPermit = 5;
  private final Semaphore semaphore = new Semaphore(10);
  private final CalcExecutor executor = new CalcExecutor(10, this);

  // server
  private Thread serverThread;
  private ServerSocketChannel ssc;
  private Selector serverSelector;
  private InetSocketAddress id; // ID du noeud
  private boolean acceptNewNode;
  private HashSet<InetSocketAddress> waitingForConnect = new HashSet<InetSocketAddress>();
  private final Object acceptLock = new Object();
  // client
  private Thread clientThread;
  private SocketChannel sc;
  private Selector clientSelector;
  private InetSocketAddress serverAddress; // ID du père
  private Context uniqueContext;
  private final Object redirectionLock = new Object();
  private boolean redirectionActive;
  private InetSocketAddress redirectionInfo;

  // Constructeur mode ROOT
  public ClientServer(int port, String outFolder) throws IOException {
    Objects.requireNonNull(outFolder);
    this.outFolder = outFolder;
    this.ssc = ServerSocketChannel.open();
    this.id = new InetSocketAddress("localhost", port);
    ssc.bind(id);
    this.serverSelector = Selector.open();
    this.console = Thread.ofPlatform().name("Console").unstarted(this::consoleGetCommand);
    this.commands = Thread.ofPlatform().name("Commands").unstarted(this::processCommands);
    this.rooter = new Rooter();
    this.acceptNewNode = false;
  }

  // constructeur mode NORMAL
  public ClientServer(int port, String outFilename, InetSocketAddress inetSocketAdress) throws IOException {
    // coté serveur
    this(port, outFilename);
    Objects.requireNonNull(inetSocketAdress);
    // coté client
    this.serverAddress = inetSocketAdress;
    this.sc = SocketChannel.open();
    this.clientSelector = Selector.open();
  }

  public Rooter getRooter() {
    return rooter;
  }

  public InetSocketAddress getID() {
    return id;
  }

  public InetSocketAddress getServerAddress() {
    return serverAddress;
  }
  
  
  public Selector getClientSelector() {
	  return clientSelector;
  }
  
  public Selector getServerSelector() {
	  return serverSelector;
  }
  
  public String getOutFolder() {
	  return outFolder;
  }
  
  public Mode getMode() {
	  return mode;
  }
  
  public Semaphore getSemaphore() {
	  return semaphore;
  }
  
  public Context getContext() {
	  return uniqueContext;
  }

  /**
   * traite la key donné en argument selon le mode :)
   * 
   */
  public void treatKey(SelectionKey key) {
    try {
      if (key.isValid() && key.isAcceptable()) {
        // l'application accepte un fils
        doAccept();
      }
    } catch (IOException ioe) {
      // lambda call in select requires to tunnel IOException
      throw new UncheckedIOException(ioe);
    }

    try {

      if (mode == Mode.NORMAL && key.isValid() && key.isConnectable()) {
        // l'application se connecte à son père
        var newClient = ((Context) key.attachment());
        newClient.doConnect();
      }
      if (key.isValid() && key.isWritable()) {
        var context = ((Context) key.attachment());
        if (!context.isClosed()) {
          context.doWrite();
        }
      }
      if (key.isValid() && key.isReadable()) {
        var context = ((Context) key.attachment());
        if (!context.isClosed()) {
          context.doRead();
        }
      }
      if (redirectionActive) {
        // est appelé autant de fois qu'il y a de clé sur l'app :/
        redirection();
      }
      
    } catch (ConnectException ce) {
      logger.severe("The father is not connected");
      close();
    } catch (IOException e) {
      logger.log(Level.INFO, "Connection closed with client due to IOException", e);
      silentlyClose(key);
    } 

  }

  public void silentlyClose(SelectionKey key) {
    Channel sc = (Channel) key.channel();
    try {
      sc.close();
    } catch (IOException e) {
      // ignore exception
    }
  }

  /**
   * Lance le serveur et le client selon le mode dans differents threads
   * puis lance la console
   * 
   */
  public void launch() throws IOException {
    switch (mode) {
      case ROOT -> {
        logger.info("ROOT mode");

        serverThread = Thread.ofPlatform().name("Server").unstarted(() -> {
          try {
            launchServer();
          } catch (IOException e) {
            e.printStackTrace();
          }
        });
        serverThread.start();

        logger.info("server ready");
        break;
      }
      case NORMAL -> {
        logger.info("NORMAL mode");
        serverThread = Thread.ofPlatform().name("Server").unstarted(() -> {
          try {
            launchServer();
          } catch (IOException e) {
            e.printStackTrace();
          }
        });
        serverThread.start();
        logger.info("server ready");

        clientThread = Thread.ofPlatform().name("Client").unstarted(() -> {
          try {
            launchClient();
          } catch (IOException e) {
            e.printStackTrace();
          }
        });
        // clientThread.setDaemon(true);
        clientThread.start();
        logger.info("client ready");
        break;
      }
    default -> throw new IllegalArgumentException("Unexpected value: " + mode);
    }
    // gestion de la console
    console.setDaemon(true);
    console.start();
    commands.setDaemon(true);
    commands.start();
    // début de la vie de l'app
    if (mode == Mode.ROOT) {
      acceptNewNode = true;
    }

  };

  /**
   * Ferme proprement l'application
   */
  @SuppressWarnings("removal")
  public void close() {
    executor.shutdown();
    try {
      this.ssc.close();
      for (SelectionKey key : serverSelector.keys()) {
        if (!key.isValid() || key.attachment() == null)
          continue;
        Context context = (Context) key.attachment();
        context.silentlyClose();
      }
      serverSelector.close();
      if (mode == Mode.NORMAL) {
        this.sc.close();
        for (SelectionKey key : clientSelector.keys()) {
          if (!key.isValid() || key.attachment() == null)
            continue;
          Context context = (Context) key.attachment();
          context.silentlyClose();
        }
        clientSelector.close();
        this.clientThread.stop();
      }
      this.serverThread.stop();

    } catch (IOException e) {
      e.printStackTrace();
    }
  }

  /**
   * console qui reçoit des commandes dans l'entrée standard
   * 
   */
  public void consoleGetCommand() {
    try {
      logger.info("Console started");
      var scanner = new Scanner(System.in);
      while (scanner.hasNextLine() && !Thread.interrupted()) {
        var cmd = scanner.nextLine();
        if (cmd != null) {
          sendCommand(cmd);
        } else {
          System.out.println("command null");
        }

      }
      scanner.close();
    } finally {
      logger.info("Console thread stopping");
    }
  }

  /**
   * envoie une commande dans la consoleQueue
   * 
   */
  public void sendCommand(String cmd) {
    if (cmd == null) {
      return;
    }
    synchronized (consoleLock) {
      consoleQueue.add(cmd);
    }
  }

  /**
   * prends un message de la consoleQueue et la traite
   * 
   */
  public void processCommands() {
    while (!Thread.interrupted()) {
      if (consoleQueue.isEmpty()) {
        continue;
      }
      synchronized (consoleLock) {

        var cmds = consoleQueue.poll().split(" ");
        if (cmds.length == 1) {
          String cmd = cmds[0].toUpperCase();
          switch (cmd) {
            case "INFO" -> {
              
              var msgInfo = new StringBuilder(">>>\tApplication mode : ");
              switch (mode) {
              case ROOT ->{
                msgInfo.append("ROOT");
              }
              case NORMAL -> {
                msgInfo.append("NORMAL\n\tConnected father : " + serverAddress);
              }
              default -> throw new IllegalArgumentException("Unexpected value: " + mode);
              }
              msgInfo.append("\n\tConnected children(s) : " + getNbChildren());
              msgInfo.append("\n\tConnected node(s) : " + rooter.getNodes().size());
              msgInfo.append("\n\tacceptNewNode : " + acceptNewNode);
              msgInfo.append("\n\tredirectionActive : " + redirectionActive);
              msgInfo.append("\n\tnbCalcul lancé: " + nextCalcIdCreated);
              msgInfo.append("\n\tID de l'app : " + id);
              System.out.println(msgInfo.toString());
            }

            case "D" -> {
              sendDisconnectMessage();
            }
            
            case "DISCONNECT" -> {
              sendDisconnectMessage();
            }

            case "R" -> {
              System.out.println(rooter.toString());
            }
            case "ROOT" -> {
              System.out.println(rooter.toString());
            }
            case "PAUSE" -> {
              acceptNewNode = !acceptNewNode;
            }

            default -> {
              var msgInfo = ">>>\tUnknown commamd. Try :";
              msgInfo += "\n\tINFO\n\tSTART url-jar fully-qualified-name start-range end-range filename\n\tDISCONNECT\n\tROOT";
              System.out.println(msgInfo);
            }
          }

        }

        else {

          if (cmds.length == 6) {
            int start = Integer.parseInt(cmds[3]);
            int end = Integer.parseInt(cmds[4]);
            var query = new Query0(nextCalcIdCreated, id, cmds[1], cmds[2], start, end);
            var calcId = new CalcId(nextCalcIdCreated, id);
            executor.addCalc(calcId, new CalcRes(cmds[5], new HashMap<Integer, String>(), end - start));
            executor.source.put(calcId, new Source(cmds[1], cmds[2]));
            try {
              processQuery(query, null);
            } catch (InterruptedException e) {
              e.printStackTrace();
            }
            nextCalcIdCreated++;
          }

          else {
            var msgInfo = ">>>\tUnknown commamd. Try :";
            msgInfo += "\n\tINFO\n\tSTART url-jar fully-qualified-name start-range end-range filename\n\tDISCONNECT\n\tROOT";
            System.out.println(msgInfo);
          }
        }
      }
    }
  }
  
  
  /**
   * traite la requete donnée en parametre en provenance de la clé
   * 
   * @param query {@link Query} à traiter
   * @param key {@link SelectionKey} qui a reçu la requête
   * @throws InterruptedException
   */
  @SuppressWarnings("preview")
  public void processQuery(Query query, SelectionKey key) throws InterruptedException {
    switch (query) {
    //départ d'une plage de calcul
    case Query0 queryf ->{
      //System.out.println("traitement de la requete 0");
      Query1 query1;
      if (semaphore.availablePermits() == 0 && key != null) {
        query1 = new Query1(queryf.getCalcId().id(), queryf.getCalcId().origin(), false, queryf.getUrl(), queryf.getClassName(), queryf.getStartRange(),
            queryf.getEndRange());
      } else {
        query1 = new Query1(queryf.getCalcId().id(), queryf.getCalcId().origin(), true);
        executor.shareCalcs(queryf, key);
      }
      if (!queryf.getCalcId().origin().equals(id)) {
        Context context = (Context) key.attachment();
        context.addNewQuery(query1);
      }
    }
    
    //réponse d'un départ d'une plage de calcul
    case Query1 queryf -> {
      //System.out.println("traitement de la requete 1");
      if (queryf.getAccept() == false) {
        var calcId = queryf.getCalcId();
        var query0 = new Query0(calcId.id(), calcId.origin(), queryf.getUrl() ,queryf.getClassName() , queryf.getStartRange(), queryf.getStartRange());
        try {
          processQuery(query0, key);
        } catch (InterruptedException e) {
          e.printStackTrace();
        }
      }
    }
    //reception d'un resultat
    case Query2 queryf -> {
      //System.out.println("traitement de la requete 2");
      var calcId = queryf.getCalcId();
      if (calcId.origin().equals(id)) {
        //System.out.println("ajout aux resultat : " + queryf.getValue());
        executor.addRes(calcId, queryf.getRes(), queryf.getValue());
        if (executor.calcIsFinished(calcId)) {
        	executor.writeRes(calcId);
        }
      } else {
        //System.out.println("redirection de res");
        executor.sendRes(calcId, queryf.getRes(), queryf.getValue());
      }
    }
    
    //deconexion d'une node
    case Query3 queryf -> {
    //System.out.println("traitement de la requete 3");
      if (queryf.getReponse() == 1 && mode.equals(Mode.NORMAL)) {
        if (queryf.getRefus() == 1) {
          logger.info("Deconexion not allowed because the father is busy. So Try later ...");
          return;
        }
        
        logger.info("Waiting to be disconnected ...");
        synchronized(acceptLock) {
          acceptNewNode = false;
        }
        synchronized(redirectionLock) {
          redirectionActive = true;
        }
        
        sendAllReco(serverAddress);
        
        synchronized(redirectionLock) {
          for (;;) {
            if (redirectionActive == false) {
              break;
            }
          }
        }
        close();
      } else {
        Byte refus = 1;
        if (!acceptNewNode) {
          sendRefusDeco(key, refus);
        } else {
          if (!queryf.getNodes().isEmpty()) {
            acceptNewNode = false;
          }
          refus = 0;
          sendRefusDeco(key, refus);
          //System.out.println("l'adresse "+ queryf.getNodeDeco() +" veut se deco et id : " + id);
          
          recupClients(queryf.getNodeDeco(), queryf.getNodes());
          // on update le rooter
          rooter.delete(rooter.get(queryf.getNodeDeco()));
          sendAllDeletedNode(key, id, queryf.getNodeDeco());
        }
      }
    }
    
    //redirection d'une node
    case Query4 queryf -> {
      //System.out.println("traitement de la requete 4");
      prepareRedirection(queryf.getNewDest());
      
      uniqueContext.closePort();
      // on update le rooter
      rooter.replaceThenDelete(serverAddress, queryf.getNewDest());
      // System.out.println("normalement on envoie à tous l'update -> parent :" +
      // queryf.getNewDest() + " deleted :" + serverAddress);
      sendAllDeletedNode(key, queryf.getNewDest(), serverAddress);
    }
    
    // redirection de calcul
    case Query5 queryf -> {
      //System.out.println("traitement de la requete 5 (pas prit en charge)");
      /*
      var calcId = queryf.getCalcId();
      var source = new Source(queryf.getUrl(), queryf.getClassName());
      executor.addCalc(calcId, new CalcRes("fromOtherApp", new HashMap<Integer, String>(), queryf.getEndRange() - queryf.getStartRange()));
      executor.source.put(calcId, source);
      */
    }
    
    //nouvelle node dans le rooter
    case Query6 queryf -> {
      //System.out.println("traitement de la requete 6");
      var context = (Context) key.attachment();
      if (queryf.getForAll() == 0) {
        var stateCo = doAcceptAnswer(key, queryf.getNodeOrigin());
        if (stateCo.equals(AcceptCo.NOT_POSSIBLE)) {
          return;
        }
        synchronized(acceptLock) {
          acceptNewNode = false; 
        }
        // definition de l'id de la node
        context.setID(queryf.getNodeOrigin());
        rooter.update(context, context);
        // information aux autre noeuds
        sendAllNewNode(key, queryf.getNodeOrigin());
        if (stateCo.equals(AcceptCo.WAITING)) {
          rooter.updateContext(queryf.getNodeOrigin(), redirectionInfo);
          waitingForConnect.remove(queryf.getNodeOrigin());
          synchronized(redirectionLock) {
            if (waitingForConnect.isEmpty()) {
              acceptNewNode = true;
              redirectionInfo = null;
            }
          }
        } else {
          synchronized(acceptLock) {
            acceptNewNode = true; 
          }
        }
      }

      else {
        var destContext = new Context(queryf.getNodeOrigin(), Mode.CONTEXT);
        rooter.update(destContext, context);
        // puis récursivité
        sendAllNewNode(key, queryf.getNodeOrigin());
      }
    }

    //réponse à une nouvelle connexion
    case Query7 queryf -> {
      //System.out.println("traitement de la requete 7");
      if (queryf.getAccept() == 0) {
        logger.info("connection refused => application closed");
        close();
      } else {
        logger.info("reception of the rooting table");
        var context = (Context) key.attachment();
        rooter.receiveAddress(context, queryf.getRooter());
        if (mode == Mode.NORMAL) {
          this.acceptNewNode = true;
        }
      }
    }
    
    //alerte d'une supression d'un noeud dans le reseau
    case Query8 queryf -> {
      //System.out.println("traitement de la requete 8");
      rooter.replaceThenDelete(queryf.getNodeDeleted(), queryf.getNodeFather());
      // recursivité
      sendAllDeletedNode(key, queryf.getNodeFather(), queryf.getNodeDeleted());
    }
    case default ->{
      logger.warning("unknown type of query");
    }
    }
    
  }

  /**
   * envoie un refus de deconexion à la clé
   * 
   * @param key   {@link SelectionKey}
   * @param refus {@link Byte} 1 ou 0
   */
  public void sendRefusDeco(SelectionKey key, Byte refus) {
    var context = (Context) key.attachment();
    context.addRefusDeco(refus);
  }

  /**
   * Envoie à tout les fils d'un noeud l'information qu'il faut se reconnecter
   * au père du noeud
   * 
   * @param addressDeco {@link InetSocketAddress} de l'adresse ou il faut se
   *                    reconnecter(le père)
   */
  public void sendAllReco(InetSocketAddress addressDeco) {
    // System.out.println("evoie à tout les fils une redirection");
    for (var key : serverSelector.keys()) {
      var context = (Context) key.attachment();
      if (context != null) {
        context.addReco(addressDeco);
        serverSelector.wakeup();
      }
    }
    
    synchronized (redirectionLock) {
      redirectionActive = false; 
    }
    
  }

  /**
   * Envoie à tout les fils et père le nouveau noeud relatif à la clientKey (sauf
   * celle-ci)
   * 
   * @param newKey {@link SelectionKey} du nouveau noeud dans le réseau qui envoie
   * @param newAddress {@link InetSocketAddress} du nouveau noeud dans le réseau
   */
  public void sendAllNewNode(SelectionKey newKey, InetSocketAddress newAddress) {

    // envoie à tout les fils
    // var newAddress = getId(newKey);
    for (var key : serverSelector.keys()) {
      var context = (Context) key.attachment();
      if (context != null && !key.equals(newKey) && key.isValid()) {
        context.addNewNode(newAddress, id);
        serverSelector.wakeup();
      }
    }

    // envoie au père
    if (mode == Mode.NORMAL && !newKey.equals(uniqueContext.getKey())) {
      uniqueContext.addNewNode(newAddress, id);
      clientSelector.wakeup();
    }
  };

  /**
   * Envoie à tout les fils et père relatif à la clientKey (sauf celle-ci)
   * l'adresse du noeud qui
   * supprimé dans le réseau et son père
   * 
   * @param newKey      {@link SelectionKey} du noeud dans le réseau à ne pas
   *                    prévenir
   * @param nodeFather  l'{@link InetSocketAddress} du noeud parent.
   * @param nodeDeleted l'{@link InetSocketAddress} du noeud supprimé dans le
   *                    reseau.
   * 
   */
  public void sendAllDeletedNode(SelectionKey newKey, InetSocketAddress nodeFather, InetSocketAddress nodeDeleted) {

    // envoie à tout les fils
    for (var key : serverSelector.keys()) {
      var context = (Context) key.attachment();
      if (context != null && !key.equals(newKey) && key.isValid()) {
        context.addDeletedNode(nodeFather, nodeDeleted);
        serverSelector.wakeup();
      }
    }

    // envoie au père
    if (mode == Mode.NORMAL && !newKey.equals(uniqueContext.getKey())) {
      uniqueContext.addDeletedNode(id, nodeDeleted);
      clientSelector.wakeup();
    }
  }

  /**
   * Envoie à tout les noeuds d'un rooter à une key
   * 
   * @param rooter  {@link Rooter} qui contient tout les noeuds à envoyer
   * @param toKey {@link SelectionKey} du noeud qui reçoit les address
   */
  public void sendRooter(Rooter rooter, SelectionKey toKey) {
    var context = (Context) toKey.attachment();
    var addressOrigin = getId(toKey);
    var list = rooter.getAddress();
    list.remove(addressOrigin);
    context.addRooter(id, list);
  }

  /**
   * Envoie un message à son père si il est en mode Normal
   * Sinon si le root n'a pas de fils connecté alors il peut se fermer
   */
  public void sendDisconnectMessage() {
    if (semaphore.availablePermits() < nbCalculPermit) {
      logger.info("Deconexion is not allowed because calculations have not been completed. So please try later ...");
      return;
    }
    synchronized(acceptLock) {
      acceptNewNode = false;
    }
    if (mode == Mode.NORMAL) {
      System.out.println(">>>\tNormal Mode try to disconnecting ...");
      uniqueContext.addDeco(id, getChildren());
      clientSelector.wakeup();
    } else {
      System.out.println(">>>\tRoot Mode try to disconnecting ...");
      if (getNbChildren() > 0) {
        System.out.println(">>>\tImpossible because some clients are connected");
        synchronized(acceptLock) {
          acceptNewNode = true;
        }
      } else {
        close();
      }
    }
  }

  /**
   * récupère l'ID d'un noeud à partir d'une key
   * 
   * @param key SelectionKey cible
   * @return InetSocketAddress l'ID du noeud relié à la clé
   */
  public InetSocketAddress getId(SelectionKey key) {
    var context = (Context) key.attachment();
    return context.getId();
  }

  /*
   * ******************************************************
   * * CLIENT *
   * ******************************************************
   */

  /**
   * prépare le noeud à se rediriger vers un nouveau serveur
   * 
   * @param newServer {@link InetSocketAddress} adresse du noeuveau
   * @warning problème de concurence à fixer
   */
  private void prepareRedirection(InetSocketAddress newServer) {
    synchronized(redirectionLock) {
      acceptNewNode = false;
      redirectionActive = true;
      redirectionInfo = newServer;
    }
  }

  /**
   * le client se connecte du serveur courrant puis se
   * connecte vers le nouveau serveur attribué via
   * preparedRedirection
   * 
   * @warning problème de concurence à fixer
   */
  private void redirection() {
    synchronized(redirectionLock) {
    if (!redirectionActive) {
      return;
    }

    var newServer = redirectionInfo;

    for (SelectionKey oldkey : clientSelector.keys()) {
      if (!oldkey.isValid() || oldkey.attachment() == null)
        continue;
      Context context = (Context) oldkey.attachment();
      context.silentlyClose();
    }

    try {
      this.sc.close();
    } catch (IOException e) {
      e.printStackTrace();
    }

    try {
      serverAddress = newServer;
      this.sc = SocketChannel.open(newServer);
      sc.configureBlocking(false);
      var newkey = sc.register(clientSelector, SelectionKey.OP_CONNECT);
      uniqueContext = new Context(this, newkey, Mode.NORMAL);
      uniqueContext.setID(serverAddress);
      newkey.attach(uniqueContext);
      logger.info("There is an attempt to redirect ...");
      
      
      uniqueContext.doConnect();
    } catch (IOException e1) {
      e1.printStackTrace();
      return;
    }

    acceptNewNode = true;
    redirectionActive = false;
    logger.info("The new server is : " + newServer);
  }
  }

  /**
   * lance le client qui se connecte à son père
   * @throws IOException
   */
  private void launchClient() throws IOException {
    sc.configureBlocking(false);
    var key = sc.register(clientSelector, SelectionKey.OP_CONNECT);
    uniqueContext = new Context(this, key, Mode.NORMAL);
    uniqueContext.setID(serverAddress);
    key.attach(uniqueContext);
    sc.connect(serverAddress);

    while (!Thread.interrupted()) {
      try {
        clientSelector.select(this::treatKey);
      } catch (UncheckedIOException tunneled) {
        throw tunneled.getCause();
      }
    }
  }

  /*
   * ********************************************************
   * * SERVER *
   * ********************************************************
   */
  
  /**
   * ajoute à waitingForConnect toutes les prochains noeud qui sont en cours de redirection
   * @param deco {@link InetSocketAddress} d'ou viennent la liste de noeud
   * @param nodes {@link ArrayList<InetSocketAddress>} liste de noeuds à ajouter dans waitingFor Connect
   */
  private void recupClients(InetSocketAddress deco, ArrayList<InetSocketAddress> nodes) {
    synchronized (redirectionLock) {
      //l'app est au courant des futurs clients
      waitingForConnect.addAll(nodes);
      redirectionInfo = deco;
    }
  }
  
  /**
   * renvoie la liste des fils connectés au noeud courant
   * 
   * @return {@link ArrayList<InetSocketAddress>} Liste d'InetSocketAddress
   */
  public ArrayList<InetSocketAddress> getChildren() {
    var fils = new ArrayList<InetSocketAddress>();
    for (var key : serverSelector.keys()) {
      var context = (Context) key.attachment();
      if (context != null && !context.isClosed()) {
        fils.add(getId(key));
      }

    }
    return fils;
  }


  /**
   * @return {@link Integer} le nombre de fils actuellement connecté aux noeuds
   */
  public int getNbChildren() {
    return getChildren().size();
  }
  
  /**
   * accepte une connexion client sur une clé alétoire
   * attache en plus à la clé un context
   * @throws IOException
   */
  public void doAccept() throws IOException {
    SocketChannel sc = ssc.accept();
    if (sc == null) {
      return;
    }
    sc.configureBlocking(false);
    var clientKey = sc.register(serverSelector, SelectionKey.OP_READ);
    clientKey.attach(new Context(this, clientKey, Mode.ROOT));
  }

  /**
   * Renvoie oui ou non si un nouveau noeud peut se connecter.
   * Envoie en réponse au Noeud relié à la clé la requête approprié
   * 
   * @param clientKey {@link SelectionKey} qui souhaite se connecter
   * @param idAddress l'{@link InetSocketAddress} de la clé qui se connecte
   * @return un enum {@link AcceptCo} qui indique si la connexion est possible
   */
  public AcceptCo doAcceptAnswer(SelectionKey clientKey, InetSocketAddress idAddress) {
    AcceptCo state;
    var msg = "Connecting a client => ";
    Context context = (Context) clientKey.attachment();
    if (acceptNewNode) {
      msg += "Sucessful";
      sendRooter(rooter, clientKey);
      state = AcceptCo.POSSIBLE;
    } else if (waitingForConnect.contains(idAddress)) {
      msg += "From a client who has disconnnected from his own father";
      state = AcceptCo.WAITING;
    } else {
      msg += "But Refused :(";
      Byte a = 0;
      context.addAccept(a);
      state = AcceptCo.NOT_POSSIBLE;
    }
    logger.info(msg);
    return state;
  }

  /**
   * lance le serveur qui analyse tout les keys
   * 
   */
  public void launchServer() throws IOException {
    ssc.configureBlocking(false);
    ssc.register(serverSelector, SelectionKey.OP_ACCEPT);
    while (!Thread.interrupted()) {
      try {
        serverSelector.select(this::treatKey);
      } catch (UncheckedIOException tunneled) {
        throw tunneled.getCause();
      } catch (ClosedSelectorException e) {
        logger.info("Selector closed : end of treatKey");
        break;
      }
    }
  }

  /**
   * parse les arguments pour lancer le bon mode.
   * 
   * @param args tableau de {@link String} contenant les arguments
   */
  public static void parseUsage(String[] args) throws IOException {
    ClientServer cs = null;
    if (args.length != 2 && args.length != 4) {
      usage();
      return;
    } else if (!(new File(args[1])).exists()) {
      System.out.println("Impossible to start the app because the folder " + args[1] + " doesn't exist.");
      return;
    } else if (args.length == 2) {
      cs = new ClientServer(Integer.parseInt(args[0]), args[1]);
      cs.mode = Mode.ROOT;
    } else if (args.length == 4) {
      cs = new ClientServer(Integer.parseInt(args[0]), args[1],
          new InetSocketAddress(args[2], Integer.parseInt(args[3])));
      cs.mode = Mode.NORMAL;
    }
    cs.launch();
  }

  public static void usage() {
    System.out.println("Usage         :");
    System.out.println("- ROOT mode   : UgeGreed.jar listenPort outFolder");
    System.out.println("- NORMAL mode : UgeGreed.jar listenPort outFolder hostname connexionPort");
  }

  public static void main(String[] args) throws NumberFormatException, IOException {
    parseUsage(args);
  }

}