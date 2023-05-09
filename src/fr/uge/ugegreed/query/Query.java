package fr.uge.ugegreed.query;

public sealed class Query permits Query0, Query1, Query2, Query3, Query4, Query5, Query6, Query7, Query8{

  protected int id;
  
  protected Query(int id){
    if (id < 0 || id > 8) {
      throw new IllegalArgumentException("the id of request must be between 0 and 8");
    }
    this.id = id;
  }
  public int id() {
    return id;
  }
  
}
