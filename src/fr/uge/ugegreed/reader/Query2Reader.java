package fr.uge.ugegreed.reader;

import java.net.InetSocketAddress;
import java.nio.ByteBuffer;

import fr.uge.ugegreed.query.Query2;

public class Query2Reader implements Reader<Query2> {
		  
	  private enum State {
	    DONE,
	    WAITING_FOR_CALC_ID, 
	    WAITING_FOR_ADDRESS_ORIGIN, 
	    WAITING_FOR_VALUE,
	    WAITING_FOR_RES,
	    ERROR
	  };
	  
	  private State state = State.WAITING_FOR_CALC_ID;
	  private IntReader intReader = new IntReader();
	  private StringReader stringReader = new StringReader();
	  private AddressReader addressReader = new AddressReader();
	  private Query2 query;
	  
	  private int id;
	  private InetSocketAddress origin;
	  private int value;
	  private String res;
	  
	  @SuppressWarnings("incomplete-switch")
	  @Override
	  public ProcessStatus process(ByteBuffer bb) {
	    if (state == State.DONE || state == State.ERROR) {
	      throw new IllegalStateException();
	    }
	    
	    for (;;) {
	      switch(state) {
	      case WAITING_FOR_CALC_ID:
	        var intReaderState = intReader.process(bb);
	        switch (intReaderState) {
	          case REFILL:
	            System.out.println("refill forAll (intReader)");  
	        return ProcessStatus.REFILL;
	      case ERROR:
	        System.out.println("erreur forAll (intReader)");
	        return ProcessStatus.ERROR;
	      default:
	        break;
		  }
		  id = intReader.get();
		  intReader.reset();
		  state = State.WAITING_FOR_ADDRESS_ORIGIN;
		  continue;
	    
		  case WAITING_FOR_ADDRESS_ORIGIN:
		    var addressReaderState = addressReader.process(bb);
		    switch (addressReaderState) {
		      case REFILL:
		        System.out.println("refill origin (addressReader)");
		        return ProcessStatus.REFILL;
		      case ERROR:
		        return ProcessStatus.ERROR;
		      default:
		        break;
		    }
		    origin = addressReader.get();
		    addressReader.reset();
		    state = State.WAITING_FOR_VALUE;
		    continue;
		  case WAITING_FOR_VALUE:
		    intReaderState = intReader.process(bb);
		    switch (intReaderState) {
		      case REFILL:
		        System.out.println("refill dest (stringReader)");
		        return ProcessStatus.REFILL;
		      case ERROR:
		        return ProcessStatus.ERROR;
		      default:
		        break;
		    }
		    value = intReader.get();
		    intReader.reset();
		    state = State.WAITING_FOR_RES;
		    continue;
		  case WAITING_FOR_RES:
		        var stringReaderState = stringReader.process(bb);
		        switch (stringReaderState) {
		          case REFILL:
		            System.out.println("refill dest (addressReader)");
			            return ProcessStatus.REFILL;
			          case ERROR:
			            return ProcessStatus.ERROR;
			          default:
			            break;
			        }
			        res = stringReader.get();
			        stringReader.reset();
			        query = new Query2(id, origin, value, res);
			        state = State.DONE;
			        continue;
		      case DONE:
		        return ProcessStatus.DONE;
		      }
	    }
	  }
	  
	  @Override
	  public Query2 get() {
	    if (state != State.DONE) {
	      throw new IllegalStateException();
	    }
	    return query;
	  }
	
	  @Override
	  public void reset() {
	    state = State.WAITING_FOR_CALC_ID;
	    stringReader.reset();
	    intReader.reset();
	    addressReader.reset();
	  }
}
