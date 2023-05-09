package fr.uge.ugegreed.reader;

import java.net.InetSocketAddress;
import java.nio.ByteBuffer;

import fr.uge.ugegreed.query.Query8;

public class Query8Reader implements Reader<Query8> {
  
  private enum State {
    DONE,
    WAITING_FOR_ADDRESS_FATHER, WAITING_FOR_ADDRESS_DELETED,
    ERROR
  };
  
  private State state = State.WAITING_FOR_ADDRESS_FATHER;
  private AddressReader addressReader = new AddressReader();
  private Query8 query;
  
  private InetSocketAddress father = null;
  private InetSocketAddress deleted = null;
  
  @SuppressWarnings("incomplete-switch")
  @Override
  public ProcessStatus process(ByteBuffer bb) {
    if (state == State.DONE || state == State.ERROR) {
      throw new IllegalStateException();
    }
    
    
    for (;;) {
      switch(state) {
      case WAITING_FOR_ADDRESS_FATHER:
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
        father = addressReader.get();
        addressReader.reset();
        state = State.WAITING_FOR_ADDRESS_DELETED;
        continue;
      case WAITING_FOR_ADDRESS_DELETED:
        addressReaderState = addressReader.process(bb);
        switch (addressReaderState) {
          case REFILL:
            System.out.println("refill dest (addressReader)");
            return ProcessStatus.REFILL;
          case ERROR:
            return ProcessStatus.ERROR;
          default:
            break;
        }
        deleted = addressReader.get();
        addressReader.reset();
        query = new Query8(father, deleted);
        state = State.DONE;
        continue;
      case DONE:
        return ProcessStatus.DONE;
      }
    }
  }
  
  @Override
  public Query8 get() {
    if (state != State.DONE) {
      throw new IllegalStateException();
    }
    return query;
  }

  @Override
  public void reset() {
    state = State.WAITING_FOR_ADDRESS_FATHER;
    addressReader.reset();
  }
}
