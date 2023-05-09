package fr.uge.ugegreed.query;

import java.net.InetSocketAddress;
import java.util.Objects;

import fr.uge.ugegreed.calc.CalcId;

public final class Query1 extends Query{
	
	
  private CalcId calcId;
	private boolean accept;
	private String url;
  private String className;
	private int startRange;
	private int endRange;
	
  
  /* ********************************************************
   * *        ID 1 (Réponse à la demande de calcul)        			    *
   * ******************************************************** 
  */
  
  public Query1(int id, InetSocketAddress origin, boolean accept) {
    super(1);
    Objects.requireNonNull(origin);
    if (accept != true) {
      throw new IllegalArgumentException("accept must be true");
    }
    this.calcId =new CalcId(id, origin);
    this.accept = accept;
  }
  
  public Query1(int id, InetSocketAddress origin, boolean accept, String url, String className, int startRange, int endRange) {
	super(1);
	Objects.requireNonNull(origin);
	this.calcId =new CalcId(id, origin);
	this.accept = accept;	
	this.className = className;
	this.url = url;
	this.startRange = startRange;
	this.endRange = endRange;
  }
  
  public CalcId getCalcId() {
	if (id != 1) {
      throw new IllegalStateException();
    }
    return calcId;
  }
  
  public boolean getAccept() {
    if (id != 1) {
      throw new IllegalStateException();
    }
    return accept;
  };
  
  public int getStartRange() {
    if (id != 1 || accept != false) {
      throw new IllegalStateException();
    }
    return startRange;
  };
  
  public int getEndRange() {
    if (id != 1 || accept != false) {
      throw new IllegalStateException();
    }
    return endRange;
  };
  
  public String getUrl() {
    if (id != 1 || accept != false) {
      throw new IllegalStateException();
    }
    return url;
  }
  
  public String getClassName() {
    if (id != 1 || accept != false) {
      throw new IllegalStateException();
    }
    return className;
  }
}
