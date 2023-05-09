package fr.uge.ugegreed.reader;

import java.net.InetSocketAddress;
import java.nio.ByteBuffer;


public class AddressReader implements Reader<InetSocketAddress> {

  private enum State {
    DONE, WAITING_FOR_HOST, WAITING_FOR_PORT, ERROR
  };
  
  private State state = State.WAITING_FOR_HOST;
  private int BUFFER_SIZE = 1024;
  private IntReader intReader = new IntReader();
  private StringReader stringReader = new StringReader();
  private final ByteBuffer internalBufferTexte = ByteBuffer.allocate(BUFFER_SIZE);
  private InetSocketAddress address;
  
  @SuppressWarnings({"incomplete-switch" })
  @Override
  public ProcessStatus process(ByteBuffer bb) {
    if (state == State.DONE || state == State.ERROR) {
      throw new IllegalStateException();
    }
    
      String hostname = null ;
      int port = 0 ;
      
      for (;;) {
        switch(state) {
          case WAITING_FOR_HOST:
            var stringReaderState = stringReader.process(bb);
            switch (stringReaderState) {
              case REFILL:
                System.out.println("refill host (stringReader)");
                return ProcessStatus.REFILL;
              case ERROR:
                System.out.println("erreur host (stringReader)");
                return ProcessStatus.ERROR;
            }
            hostname = stringReader.get();
            stringReader.reset();
            state = State.WAITING_FOR_PORT;
            continue;
          case WAITING_FOR_PORT:
            var intReaderState = intReader.process(bb);
            switch (intReaderState) {
              case REFILL:
                System.out.println("refill port (intReader)");
                return ProcessStatus.REFILL;
              case ERROR:
                System.out.println("erreur port (intReader)");
                return ProcessStatus.ERROR;
            }
            port = intReader.get();
            intReader.reset();
            state = State.DONE;
            continue;
          case DONE:
            address = new InetSocketAddress(hostname, port);
            return ProcessStatus.DONE;
            
        }
      }
  }

  @Override
  public InetSocketAddress get() {
    if (state != State.DONE) {
      throw new IllegalStateException();
    }
    return address;
  }

  @Override
  public void reset() {
    state = State.WAITING_FOR_HOST;
    intReader.reset();
    stringReader.reset();
    internalBufferTexte.clear();
    
  }

}
