package fr.uge.ugegreed.reader;

import java.net.InetSocketAddress;
import java.nio.ByteBuffer;

import fr.uge.ugegreed.query.Query6;

public class Query6Reader implements Reader<Query6> {
  
  private enum State {
    DONE,
    WAITING_FOR_FOR_ALL, WAITING_FOR_ADDRESS_ORIGIN, WAITING_FOR_ADDRESS_DEST,
    ERROR
  };
  
  private State state = State.WAITING_FOR_FOR_ALL;
  private IntReader intReader = new IntReader();
  private ByteReader byteReader = new ByteReader();
  private AddressReader addressReader = new AddressReader();
  private Query6 query;
  
  private InetSocketAddress origin = null;
  private InetSocketAddress dest = null ;
  private Byte forAll = 0;
  
  @SuppressWarnings("incomplete-switch")
  @Override
  public ProcessStatus process(ByteBuffer bb) {
    if (state == State.DONE || state == State.ERROR) {
      throw new IllegalStateException();
    }
    
    for (;;) {
      switch(state) {
      case WAITING_FOR_FOR_ALL:
        var byteReaderState = byteReader.process(bb);
        switch (byteReaderState) {
          case REFILL:
            //System.out.println("refill forAll (byteReader)");  
            return ProcessStatus.REFILL;
          case ERROR:
            //System.out.println("erreur forAll (byteReader)");
            return ProcessStatus.ERROR;
          default:
            break;
        }
        forAll = byteReader.get();
        byteReader.reset();
        state = State.WAITING_FOR_ADDRESS_ORIGIN;
        continue;
      case WAITING_FOR_ADDRESS_ORIGIN:
        var addressReaderState = addressReader.process(bb);
        switch (addressReaderState) {
          case REFILL:
            //System.out.println("refill origin (addressReader)");
            return ProcessStatus.REFILL;
          case ERROR:
            return ProcessStatus.ERROR;
          default:
            break;
        }
        origin = addressReader.get();
        addressReader.reset();
        if (forAll == 0) {
          query = new Query6(origin);
          state = State.DONE;
        } else {
          state = State.WAITING_FOR_ADDRESS_DEST;
        }
        continue;
      case WAITING_FOR_ADDRESS_DEST:
        addressReaderState = addressReader.process(bb);
        switch (addressReaderState) {
          case REFILL:
            //System.out.println("refill dest (addressReader)");
            return ProcessStatus.REFILL;
          case ERROR:
            return ProcessStatus.ERROR;
          default:
            break;
        }
        dest = addressReader.get();
        addressReader.reset();
        query = new Query6(origin, dest);
        state = State.DONE;
        continue;
      case DONE:
        return ProcessStatus.DONE;
      }
    }
  }
  
  @Override
  public Query6 get() {
    if (state != State.DONE) {
      throw new IllegalStateException();
    }
    return query;
  }

  @Override
  public void reset() {
    state = State.WAITING_FOR_FOR_ALL;
    byteReader.reset();
    intReader.reset();
    addressReader.reset();
  }
}
