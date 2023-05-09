package fr.uge.ugegreed.query;

import java.net.InetSocketAddress;
import java.util.Objects;

import fr.uge.ugegreed.calc.CalcId;

public final class Query0 extends Query{
	
	private CalcId calcId;
	private String url;
	private String className;
	private int startRange;
	private int endRange;
	
  
  /* ********************************************************
   * *        ID 0 (Requête de calcul)        			    *
   * ******************************************************** 
  */
  
  // Root
  public Query0(int id, InetSocketAddress origin, String url, String className, int startRange, int endRange) {
    super(0);
    Objects.requireNonNull(origin);
    this.calcId =new CalcId(id, origin);
    this.url = Objects.requireNonNull(url);
    this.className = Objects.requireNonNull(className);
    this.startRange = startRange;
    this.endRange = endRange;
  }
  
  public CalcId getCalcId() {
	  if (id != 0) {
	      throw new IllegalStateException();
	    }
	    return calcId;
  }
  
  public String getUrl() {
    if (id != 0) {
      throw new IllegalStateException();
    }
    return url;
  };
  
  public String getClassName() {
    if (id != 0) {
      throw new IllegalStateException();
    }
    return className;
  };
  
  public int getStartRange() {
    if (id != 0) {
      throw new IllegalStateException();
    }
    return startRange;
  };
  
  public int getEndRange() {
    if (id != 0) {
      throw new IllegalStateException();
    }
    return endRange;
  };
}
