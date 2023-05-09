package fr.uge.ugegreed.query;

import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.Objects;

public final class Query3 extends Query{
  private InetSocketAddress nodeDeco;
  private ArrayList<InetSocketAddress> nodes;
  private Byte reponse;
  private Byte refus;
  
  /**
   * 
   *  ID 3 (noeud qui se deconnecte dans le reseau)     
   * 
   * @param nodeDeco {@link InetSocketAddress} noeud qui se déco du reseau
   * @param nodes {@link Set<InetSocketAddress>} emsembles des nodes ratachés au noeud qui se déco
  */
  public Query3(InetSocketAddress nodeDeco, ArrayList<InetSocketAddress> nodes) {
    super(3);
    Objects.requireNonNull(nodeDeco);
    Objects.requireNonNull(nodes);
    this.reponse = 0;
    this.nodeDeco = nodeDeco;
    this.nodes = nodes;
  }
  
  /**
   * 
   *  ID 3 (reponse à la possibilité de deconnexion)
   * 
   * @param refus {@link Byte} qui indique si la deconnexion est possible (0 ou 1)
   * 
  */
  public Query3(Byte refus) {
    super(3);
    if (refus < 0 || refus > 1) {
      throw new IllegalArgumentException("refus must be equals to 1 or 0");
    }
    this.reponse = 1;
    this.refus = refus;
  }
  
  public InetSocketAddress getNodeDeco() {
    if (id != 3) {
      throw new IllegalStateException();
    }
    return nodeDeco;
  };
  
  public Byte getReponse() {
    if (id != 3) {
      throw new IllegalStateException();
    }
    return reponse;
  }
  
  public Byte getRefus() {
    if (id != 3 || reponse == 0) {
      throw new IllegalStateException();
    }
    return refus;
  }
  
  public ArrayList<InetSocketAddress> getNodes() {
    if (id != 3 || reponse == 1) {
      throw new IllegalStateException();
    }
    return nodes;
  }
}
