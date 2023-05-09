package fr.uge.ugegreed.reader;

import java.net.InetSocketAddress;
import java.nio.ByteBuffer;

import fr.uge.ugegreed.query.Query4;

public class Query4Reader implements Reader<Query4> {
  
  private enum State {
    DONE,
    WAITING_FOR_ADDRESS_DEST,
    ERROR
  };
  
  private State state = State.WAITING_FOR_ADDRESS_DEST;
  private AddressReader addressReader = new AddressReader();
  private Query4 query;
  
  private InetSocketAddress dest = null ;
  
  @SuppressWarnings("incomplete-switch")
  @Override
  public ProcessStatus process(ByteBuffer bb) {
    if (state == State.DONE || state == State.ERROR) {
      throw new IllegalStateException();
    }
    
    for (;;) {
      switch(state) {
      case WAITING_FOR_ADDRESS_DEST:
        var addressReaderState = addressReader.process(bb);
        switch (addressReaderState) {
          case REFILL:
            System.out.println("refill dest (addressReader)");
            return ProcessStatus.REFILL;
          case ERROR:
            return ProcessStatus.ERROR;
          default:
            break;
        }
        dest = addressReader.get();
        addressReader.reset();
        query = new Query4(dest);
        state = State.DONE;
        continue;
      case DONE:
        return ProcessStatus.DONE;
      }
    }
  }
  
  @Override
  public Query4 get() {
    if (state != State.DONE) {
      throw new IllegalStateException();
    }
    return query;
  }

  @Override
  public void reset() {
    state = State.WAITING_FOR_ADDRESS_DEST;
    addressReader.reset();
  }
}
