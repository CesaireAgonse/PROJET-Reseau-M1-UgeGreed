package fr.uge.ugegreed;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Objects;
import java.net.InetSocketAddress;

public class Rooter {
  private final HashMap<Context, Context> table;
  
  public Rooter() { 
    this.table = new HashMap<>();
  }
  
  /**
   * remplace les noeuds non linkés qui ont pour valeur
   * un context qui n'est plus dans le reseau
   * 
   * @param origin l'{@link InetSocketAddress} du noeud qui sera sa nouvelle valeur
   * @param old l'{@link InetSocketAddress} du noeud qui n'est plus dans le reseau
   */
  public void updateContext(InetSocketAddress origin, InetSocketAddress old) {
    for (var k : getNodes()) {
      if (k.getMode().equals(Mode.CONTEXT) && get(k).getId().equals(old)) {
        table.put(k, get(origin));
      }
    }
  }
  
  /**
   * Mets à jour la table de routage selon les contextes
   * 
   * @param origin le {@link Context} de l'addresse d'origine
   * @param dest le {@link Context} de la destination
   */
  public void update(Context origin, Context dest) {
    Objects.requireNonNull(origin);
    Objects.requireNonNull(dest);
    for (var k : getNodes()) {
      if (k.getId().equals(origin.getId()) && !origin.getMode().equals(k.getMode())) {
        table.put(origin, dest);
        table.remove(k);
        return;
      }
      
      if (k.getId().equals(origin.getId()) && !origin.equals(k)) {
        return;
      }
      
      if (k.getId().equals(dest.getId()) && dest.getMode() == Mode.CONTEXT) {
        table.put(origin, k);
        return;
      }
    }
    table.put(origin, dest);
  }
  
  /**
   * @param key {@link Context} clé à trouver dans la table 
   * @return le {@link Context} de la valeur associé à la clé
   */
  public Context get(Context key) {
    Objects.requireNonNull(key);
    return table.get(key);
  }
  
  /**
   * @param key {@link InetSocketAddress} clé à trouver dans la table 
   * @return le {@link Context} de la valeur associé à la clé
   */
  public Context get(InetSocketAddress key) {
    Objects.requireNonNull(key);
    for (var k : getNodes()) {
      if (k.getId().equals(key)) {
        return table.get(k);
      }
    }
    return null;
  }
  
  /**
   * 
   * @return {@link ArrayList<Context>} une liste de toutes les contexts du rooter
   */
  public ArrayList<Context> getNodes() {
    return new ArrayList<>(table.keySet());
  }

  /**
   * 
   * @return {@link ArrayList<InetSocketAddress>} une liste de toutes les addresses du rooter
   */
  public ArrayList<InetSocketAddress> getAddress() {
    ArrayList<InetSocketAddress> res = new ArrayList<>();
    for (var k : getNodes()) {
      res.add(k.getId());
    }
    return res;
  }  
  
  /**
   * affecte à toutes les clés de listAddress la valeur key<br>
   * les valeurs associées à key seront crée en tant que context "not linked"
   * 
   * @param key {@link Context} de la valeur a rajouter 
   * @param listAddress liste d'{@link InetSocketAddress} des clés qui auront pour valeur key 
   */
  public void receiveAddress(Context key, ArrayList<InetSocketAddress> listAddress) {
    Objects.requireNonNull(key);
    for (var k: listAddress) {
      var context = new Context(k, Mode.CONTEXT);
      this.update(context, key);
    }
  };
  
  
  /**
   * remplace toute les valeurs qui ont pour valeur l'ancienne clé par la nouvelle clé
   * <br> supprime ensuite l'ancienne clé
   * @param key l'{@link InetSocketAddress} de l'anncience clé
   * @param newKey l'{@link InetSocketAddress} de la nouvelle clé
   */
  public void replaceThenDelete(InetSocketAddress key, InetSocketAddress newKey) {
    Objects.requireNonNull(key);
    Objects.requireNonNull(newKey);
    for (var k : getNodes()) {
      table.replace(k, this.get(key), this.get(newKey));
    }
    delete(key);
  }
  
  /**
   * Supprime la clé dans le rooter si elle existe<br>
   * (ne supprime pas la valeur key si elle apparait dans une valeur d'une 
   * autre clé du rooter)<br>
   * supprime le dernier contexte ayant l'id comme adresse
   * @param key l'{@link InetSocketAddress} de la clé à supprimer
   */
  public void delete(InetSocketAddress key) {
    Context toDelete = null;
    for (var k : getNodes()) {
      if (k.getId().equals(key)) {
        toDelete = k;
      }
    }
    if (toDelete != null) {
      delete(toDelete);
    }
  }
  
  /**
   * Supprime la clé dans le rooter<br>
   * (ne supprime pas la valeur key si elle apparait dans une valeur d'une 
   * autre clé du rooter)
   * @param key le {@link Context} de la clé à supprimer
   */
  public void delete(Context key) {
    Objects.requireNonNull(key);
    table.remove(key);
  }

  /**
   * affiche la table de rootage<br>
   * uniquement les adresses avec leurs status dans la hiérarchie<br>
   * si l'adresse ne fait pas partie d'un de ses fils ou de son père alors il est "not linked" 
   */
  public String toString() {
    var stringBuilder= new StringBuilder();
    var separator = "\n";
    stringBuilder.append("Table de Routage [");
    for (var sockets : table.entrySet()) {
      var state = sockets.getKey().getMode();
      String stateString;
      switch(state) {
      case ROOT:
        stateString = "son";
        break;
      case CONTEXT:
        stateString = "not linked";
        break;
      case NORMAL:
        stateString = "father";
        break;
      default:
        stateString = "???";
        break;
      }
      stringBuilder.append(separator).append(sockets.getKey().getId());
      stringBuilder.append(" (").append(stateString).append(") ==> "); 
      stringBuilder.append(sockets.getValue().getId());
    }
    stringBuilder.append("\n\t]");
    return stringBuilder.toString();
  }
  
}
