package fr.uge.ugegreed.query;

import java.net.InetSocketAddress;
import java.util.ArrayList;

public final class Query7 extends Query{
  
  private Byte acceptNewNode;
  private ArrayList<InetSocketAddress> rooterList;
  private InetSocketAddress nodeOriginList;
  
  /* ********************************************************
   * *        ID 7 refus (acceptation d'un noeud dans le reseau)  *
   * ******************************************************** 
  */
  
  public Query7(Byte accept) {
    super(7);
    if (accept != 0) {
      throw new IllegalArgumentException();
    }
    this.acceptNewNode = accept;
  }
  
  public Byte getAccept() {
    if (id != 7) {
      throw new IllegalStateException();
    }
    return acceptNewNode;
  }
  
  /* ********************************************************
   * *        ID 7 succes (envoie d'une liste d'address)              *
   * ******************************************************** 
  */
  
  public Query7(InetSocketAddress nodeOriginList , ArrayList<InetSocketAddress> rooterlist) {
    super(7);
    this.rooterList = rooterlist;
    this.acceptNewNode = 1;
    this.nodeOriginList = nodeOriginList;
  }
  
  public ArrayList<InetSocketAddress> getRooter() {
    if (id != 7) {
      throw new IllegalStateException();
    }
    return rooterList;
  }
  
  public InetSocketAddress getNodeOriginList() {
    if (id != 7 && acceptNewNode != 1) {
      throw new IllegalStateException();
    }
    return nodeOriginList;
  }

}
