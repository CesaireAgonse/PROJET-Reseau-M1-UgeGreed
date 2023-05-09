package fr.uge.ugegreed.reader;

import java.net.InetSocketAddress;
import java.nio.ByteBuffer;

import fr.uge.ugegreed.query.Query0;

public class Query0Reader implements Reader<Query0> {
	  
	  private enum State {
	    DONE,
	    WAITING_FOR_CALC_ID, 
	    WAITING_FOR_ADDRESS_ORIGIN, 
	    WAITING_FOR_URL,
	    WAITING_FOR_CLASS_NAME,
	    WAITING_FOR_START_RANGE,
	    WAITING_FOR_END_RANGE,
	    ERROR
	  };
	  
	  private State state = State.WAITING_FOR_CALC_ID;
	  private IntReader intReader = new IntReader();
	  private StringReader stringReader = new StringReader();
	  private AddressReader addressReader = new AddressReader();
	  private Query0 query;
	  
	  private String url;
    private String className;
	  private int startRange;
    private int endRange;
    private int id;
	  private InetSocketAddress origin;
	  
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
	        state = State.WAITING_FOR_URL;
	        continue;
	      case WAITING_FOR_URL:
	        var stringReaderState = stringReader.process(bb);
	        switch (stringReaderState) {
	          case REFILL:
	            System.out.println("refill dest (stringReader)");
	            return ProcessStatus.REFILL;
	          case ERROR:
	            return ProcessStatus.ERROR;
	          default:
	            break;
	        }
	        url = stringReader.get();
	        stringReader.reset();
	        state = State.WAITING_FOR_CLASS_NAME;
	        continue;
	      case WAITING_FOR_CLASS_NAME:
		        stringReaderState = stringReader.process(bb);
		        switch (stringReaderState) {
		          case REFILL:
		            System.out.println("refill dest (stringReader)");
		            return ProcessStatus.REFILL;
		          case ERROR:
		            return ProcessStatus.ERROR;
		          default:
		            break;
		        }
		        className = stringReader.get();
		        stringReader.reset();
		        state = State.WAITING_FOR_START_RANGE;
		        continue;
	      case WAITING_FOR_START_RANGE:
		        intReaderState = intReader.process(bb);
		        switch (intReaderState) {
		          case REFILL:
		            System.out.println("refill dest (addressReader)");
		            return ProcessStatus.REFILL;
		          case ERROR:
		            return ProcessStatus.ERROR;
		          default:
		            break;
		        }
		        startRange = intReader.get();
		        intReader.reset();
		        state = State.WAITING_FOR_END_RANGE;
		        continue;
	      case WAITING_FOR_END_RANGE:
		        intReaderState = intReader.process(bb);
		        switch (intReaderState) {
		          case REFILL:
		            System.out.println("refill dest (addressReader)");
		            return ProcessStatus.REFILL;
		          case ERROR:
		            return ProcessStatus.ERROR;
		          default:
		            break;
		        }
		        endRange = intReader.get();
		        intReader.reset();
		        query = new Query0(id, origin, url, className, startRange, endRange);
		        state = State.DONE;
		        continue;
	      case DONE:
	        return ProcessStatus.DONE;
	      }
	    }
	  }
	  
	  @Override
	  public Query0 get() {
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
