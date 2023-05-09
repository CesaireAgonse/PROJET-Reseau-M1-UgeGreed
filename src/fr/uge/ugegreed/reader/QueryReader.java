package fr.uge.ugegreed.reader;

import java.nio.ByteBuffer;

import fr.uge.ugegreed.query.*;


public class QueryReader implements Reader<Query> {

  private enum State {
    DONE, 
    WAITING_FOR_ID, 
    WAITING_FOR_QUERY0,
    WAITING_FOR_QUERY1,
    WAITING_FOR_QUERY2,
    WAITING_FOR_QUERY3,
    WAITING_FOR_QUERY4,
    WAITING_FOR_QUERY6,
    WAITING_FOR_QUERY7,
    WAITING_FOR_QUERY8,
    ERROR
  };
  
  private State state = State.WAITING_FOR_ID;
  private IntReader intReader = new IntReader();
  private Query0Reader query0Reader = new Query0Reader();
  private Query1Reader query1Reader = new Query1Reader();
  private Query2Reader query2Reader = new Query2Reader();
  private Query3Reader query3Reader = new Query3Reader();
  private Query4Reader query4Reader = new Query4Reader();
  private Query6Reader query6Reader = new Query6Reader();
  private Query7Reader query7Reader = new Query7Reader();
  private Query8Reader query8Reader = new Query8Reader();
  private int id;
  private Query query;
  
  
  @SuppressWarnings({ "incomplete-switch", "preview" })
  private ProcessStatus WaitingID(ByteBuffer bb) {
      var intReaderState = intReader.process(bb);
      switch (intReaderState) {
        case REFILL:
          //System.out.println("refill id (intReader)");  
          return ProcessStatus.REFILL;
        case ERROR:
          //System.out.println("erreur id (intReader)");
          return ProcessStatus.ERROR;
      }
      id = intReader.get();
      intReader.reset();
      //System.out.println("ID : " + id);
      switch(id) {
        case 0 -> {
          state = State.WAITING_FOR_QUERY0;
        }
        case 1 -> {
          state = State.WAITING_FOR_QUERY1;
        }
        case 2 -> {
          state = State.WAITING_FOR_QUERY2;
        }
        case 3 -> {
          state = State.WAITING_FOR_QUERY3;
        }
        case 4 -> {
          state = State.WAITING_FOR_QUERY4;
        }
        case 6 -> {
          state = State.WAITING_FOR_QUERY6;
        }
        case 7 -> {
          state = State.WAITING_FOR_QUERY7;
        }
        case 8 -> {
	        state = State.WAITING_FOR_QUERY8;
	      }
          
        case default -> {
          System.out.println("erreur wrong id, id = " + id);
          return ProcessStatus.ERROR;
        }
      }
      return null;
  }
  
  @SuppressWarnings("incomplete-switch")
  private ProcessStatus WaitingNQuery(ByteBuffer bb, @SuppressWarnings("rawtypes") Reader queryNReader) {
    var queryReaderState = queryNReader.process(bb);
    switch (queryReaderState) {
      case REFILL:
        //System.out.println("refill queryN (queryNReader)");
        return ProcessStatus.REFILL;
      case ERROR:
        return ProcessStatus.ERROR;
    }
    query = (Query) queryNReader.get();
    query0Reader.reset();
    state = State.DONE;
    return null;
  }
  
  @SuppressWarnings("incomplete-switch")
  private ProcessStatus WaitingQuery2(ByteBuffer bb) {
	  var queryReaderState = query2Reader.process(bb);
      switch (queryReaderState) {
        case REFILL:
          //System.out.println("refill query2 (query2Reader)");
          return ProcessStatus.REFILL;
        case ERROR:
          return ProcessStatus.ERROR;
      }
      query = query2Reader.get();
      if(query == null) {
    	  System.out.println("Bad Write");
      }
      query2Reader.reset();
      state = State.DONE;
      return null;
  }
  
  
  
  @SuppressWarnings("incomplete-switch")
  @Override
  public ProcessStatus process(ByteBuffer bb) {
    if (state == State.DONE || state == State.ERROR) {
      throw new IllegalStateException();
    }
    
      for (;;) {
        switch(state) {
          case WAITING_FOR_ID:
            var error = WaitingID(bb);
            if(error == null) {
            	continue;
            }
            return error;
          case WAITING_FOR_QUERY0:
        	  error = WaitingNQuery(bb, query0Reader);
            if(error == null) {
            	continue;
            }
            return error;
          case WAITING_FOR_QUERY1:
            error = WaitingNQuery(bb, query1Reader);
            if(error == null) {
            	continue;
            }
            return error;
          case WAITING_FOR_QUERY2:
        	  error = WaitingQuery2(bb);
            if(error == null) {
            	continue;
            }
            return error;
          case WAITING_FOR_QUERY3:
            error = WaitingNQuery(bb, query3Reader);
            if(error == null) {
            	continue;
            }
            return error;
          case WAITING_FOR_QUERY4:
            error = WaitingNQuery(bb, query4Reader);
            if(error == null) {
              continue;
            }
            return error;
          case WAITING_FOR_QUERY6:
            error = WaitingNQuery(bb, query6Reader);
            if(error == null) {
            	continue;
            }
              return error;
          case WAITING_FOR_QUERY7:
            error = WaitingNQuery(bb, query7Reader);
            if(error == null) {
              continue;
            }
            return error;
          case WAITING_FOR_QUERY8:
            error = WaitingNQuery(bb, query8Reader);
            if(error == null) {
              continue;
            }
            return error;
          case DONE:
            return ProcessStatus.DONE;
        }
      }
  }

  @Override
  public Query get() {
    if (state != State.DONE) {
      throw new IllegalStateException();
    }
    return query;
  }

  @Override
  public void reset() {
    state = State.WAITING_FOR_ID;
    intReader.reset();
    query0Reader.reset();
    query1Reader.reset();
    query2Reader.reset();
    query3Reader.reset();
    query4Reader.reset();
    query6Reader.reset();
    query7Reader.reset();
    query8Reader.reset();
  }

}
