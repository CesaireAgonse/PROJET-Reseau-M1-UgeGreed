package fr.uge.ugegreed.query;

import java.net.InetSocketAddress;
import java.util.Objects;

public final class Query4 extends Query{
  private InetSocketAddress nodeDest;
  
  /**
   * ID 4 (redirection d'un noeud dans le reseau) 
   * 
   * @param nodeDest l'{@link InetSocketAddress} du noeud de redirection
  */
  public Query4(InetSocketAddress nodeDest) {
    super(4);
    Objects.requireNonNull(nodeDest);
    this.nodeDest = nodeDest;
  }
  
  public InetSocketAddress getNewDest() {
    if (id != 4) {
      throw new IllegalStateException();
    }
    return nodeDest;
  };
  
}
