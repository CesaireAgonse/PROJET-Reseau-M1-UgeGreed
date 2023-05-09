package fr.uge.ugegreed.query;

import java.net.InetSocketAddress;
import java.util.Objects;

public final class Query8 extends Query{
  private InetSocketAddress nodeFather;
  private InetSocketAddress nodeDeleted;
  
  /**
   * ID 8 (nouveau père qui remplace un noeud supprimé dans le reseau)
   * @param nodeFather {@link InetSocketAddress} père du noeud supprimé
   * @param nodeDeleted {@link InetSocketAddress} noeud supprimé
  */
  
  public Query8(InetSocketAddress nodeFather, InetSocketAddress nodeDeleted) {
    super(8);
    Objects.requireNonNull(nodeFather);
    Objects.requireNonNull(nodeDeleted);
    this.nodeFather = nodeFather;
    this.nodeDeleted = nodeDeleted;
  }
  
  public InetSocketAddress getNodeFather() {
    if (id != 8) {
      throw new IllegalStateException();
    }
    return nodeFather;
  }
  
  public InetSocketAddress getNodeDeleted() {
    if (id != 8) {
      throw new IllegalStateException();
    }
    return nodeDeleted;
  }
  
}
