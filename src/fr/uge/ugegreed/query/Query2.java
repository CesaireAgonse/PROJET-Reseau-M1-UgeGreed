package fr.uge.ugegreed.query;

import java.net.InetSocketAddress;
import java.util.Objects;

import fr.uge.ugegreed.calc.CalcId;

public final class Query2 extends Query{
	
	private CalcId calcId;
	private int value;
	private String res;
	
  
  /* ********************************************************
   * *        ID 2 (Résultat de calcul)        			    *
   * ******************************************************** 
  */
  
  public Query2(int id, InetSocketAddress origin, int value, String res) {
	super(2);
	Objects.requireNonNull(origin);
	this.calcId =new CalcId(id, origin);
	this.value = value;	
	this.res = Objects.requireNonNull(res);
  }
  
  public CalcId getCalcId() {
	if (id != 2) {
      throw new IllegalStateException();
    }
    return calcId;
  }
  
  public int getValue() {
    if (id != 2) {
      throw new IllegalStateException();
    }
    return value;
  };
  
  public String getRes() {
    if (id != 2) {
      throw new IllegalStateException();
    }
    return res;
  };
  
}
