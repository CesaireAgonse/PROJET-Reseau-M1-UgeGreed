package fr.uge.ugegreed.query;

import java.net.InetSocketAddress;
import java.util.Objects;

public final class Query6 extends Query{
  private InetSocketAddress nodeOrigin;
  private InetSocketAddress nodeDest;
  private Byte forAll;
  
  /* ********************************************************
   * *        ID 6 (nouveau noeud dans le reseau)            *
   * ******************************************************** 
  */
  
  // Root
  public Query6(InetSocketAddress nodeOrigin) {
    super(6);
    Objects.requireNonNull(nodeOrigin);
    this.forAll = 0;
    this.nodeOrigin = nodeOrigin;
  }
  
  // Not Root 
  public Query6(InetSocketAddress nodeOrigin, InetSocketAddress nodeDest) {
    super(6);
    this.id = 6;
    this.forAll = 1;
    this.nodeOrigin = Objects.requireNonNull(nodeOrigin);
    this.nodeDest = Objects.requireNonNull(nodeDest);
  }
  
  public InetSocketAddress getNodeOrigin() {
    if (id != 6) {
      throw new IllegalStateException();
    }
    return nodeOrigin;
  };
  
  public InetSocketAddress getNodeDest() {
    if (id != 6 && forAll == 1) {
      throw new IllegalStateException();
    }
    return nodeDest;
  };
  
  public Byte getForAll() {
    if (id != 6) {
      throw new IllegalStateException();
    }
    return forAll;
  }
}
