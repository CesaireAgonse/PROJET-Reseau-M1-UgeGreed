package fr.uge.ugegreed.query;

import java.net.InetSocketAddress;
import java.util.Objects;

import fr.uge.ugegreed.calc.CalcId;

public final class Query5 extends Query{
	
	private CalcId calcId;
	private String url;
	private String className;
	private int startRange;
	private int endRange;
	private InetSocketAddress nodeDeco;
	
  
  /**
   * 
  */
  public Query5(int id, InetSocketAddress origin, String url, String className, int startRange, int endRange, InetSocketAddress nodeDeco) {
    super(5);
    Objects.requireNonNull(origin);
    this.calcId =new CalcId(id, origin);
    this.url = Objects.requireNonNull(url);
    this.className = Objects.requireNonNull(className);
    this.startRange = startRange;
    this.endRange = endRange;
    this.nodeDeco = Objects.requireNonNull(nodeDeco);
  }
  
  public CalcId getCalcId() {
	  if (id != 5) {
	      throw new IllegalStateException();
	    }
	    return calcId;
  }
  
  public String getUrl() {
    if (id != 5) {
      throw new IllegalStateException();
    }
    return url;
  };
  
  public String getClassName() {
    if (id != 5) {
      throw new IllegalStateException();
    }
    return className;
  };
  
  public int getStartRange() {
    if (id != 5) {
      throw new IllegalStateException();
    }
    return startRange;
  };
  
  public int getEndRange() {
    if (id != 5) {
      throw new IllegalStateException();
    }
    return endRange;
  };
  
  public InetSocketAddress getNodeDeco() {
    if (id != 5) {
      throw new IllegalStateException();
    }
    return nodeDeco;
  }
}
