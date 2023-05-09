package fr.uge.ugegreed.reader;

import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.util.ArrayList;

import fr.uge.ugegreed.query.Query7;

public class Query7Reader implements Reader<Query7> {
  
  private enum State {
    DONE,
    WAITING_FOR_ACCEPT_NEW_NODE,
    WAITING_FOR_ADDRESS_ORIGIN_LIST, WAITING_FOR_LIST_ADDRESS_SIZE, WAITING_FOR_LIST_ADDRESS,
    ERROR
  };
  
  private State state = State.WAITING_FOR_ACCEPT_NEW_NODE;
  private IntReader intReader = new IntReader();
  private ByteReader byteReader = new ByteReader();
  private AddressReader addressReader = new AddressReader();
  private Query7 query;
  
  private int nbNodes;
  private int numNode;
  private InetSocketAddress origin = null;
  private InetSocketAddress addressRoot = null ;
  private ArrayList<InetSocketAddress> list = new ArrayList<InetSocketAddress>();
  
  @SuppressWarnings("incomplete-switch")
  @Override
  public ProcessStatus process(ByteBuffer bb) {
    if (state == State.DONE || state == State.ERROR) {
      throw new IllegalStateException();
    }
    
    for (;;) {
      switch(state) {
      case WAITING_FOR_ACCEPT_NEW_NODE:
        //lire le byte en question
        var byteReaderState = byteReader.process(bb);
        switch (byteReaderState) {
          case REFILL:
            System.out.println("refill accept (byteReader)");  
            return ProcessStatus.REFILL;
          case ERROR:
            System.out.println("erreur accept (byteReader)");
            return ProcessStatus.ERROR;
          default:
            break;
        }
        var accept = byteReader.get();
        byteReader.reset();
        if (accept == 0) {
          query = new Query7(accept);
          state = State.DONE;
        } else {
          state = State.WAITING_FOR_ADDRESS_ORIGIN_LIST;
        }
        continue;
      case WAITING_FOR_ADDRESS_ORIGIN_LIST:
        var addressReaderState = addressReader.process(bb);
        switch (addressReaderState) {
          case REFILL:
            System.out.println("refill AddressOriginList (addressReader)");
            return ProcessStatus.REFILL;
          case ERROR:
            System.out.println("erreur AddressOriginList (addressReader)");
            return ProcessStatus.ERROR;
          default:
            break;
        }
        origin = addressReader.get();
        addressReader.reset();
        state = State.WAITING_FOR_LIST_ADDRESS_SIZE;
        continue;
      case WAITING_FOR_LIST_ADDRESS_SIZE:
        var intReaderState = intReader.process(bb);
        switch (intReaderState) {
          case REFILL:
            System.out.println("refill listAddressSize (intReader)");  
            return ProcessStatus.REFILL;
          case ERROR:
            System.out.println("erreur listAddressSize (intReader)");
            return ProcessStatus.ERROR;
          default:
            break;
        }
        nbNodes = intReader.get();
        //System.out.println("NBNODES " + nbNodes);
        intReader.reset();
        state = State.WAITING_FOR_LIST_ADDRESS;
        continue;
      case WAITING_FOR_LIST_ADDRESS:
        if (numNode < nbNodes) {
          //System.out.println("SALUT");
          addressReaderState = addressReader.process(bb);
          switch (addressReaderState) {
            case REFILL:
              System.out.println("refill ListAddress node "+ numNode +" (addressReader)");
              return ProcessStatus.REFILL;
            case ERROR:
              System.out.println("erreur ListAddress (addressReader)");
              return ProcessStatus.ERROR;
            default:
              break;
          }
          addressRoot = addressReader.get();
          addressReader.reset();
          list.add(addressRoot);
          numNode++;
          continue;
        }
        query = new Query7(origin, list);
        state = State.DONE;
        continue;
      case DONE:
        return ProcessStatus.DONE;
      }
    }
  }
  
  @Override
  public Query7 get() {
    if (state != State.DONE) {
      throw new IllegalStateException();
    }
    return query;
  }

  @Override
  public void reset() {
    state = State.WAITING_FOR_ACCEPT_NEW_NODE;
    byteReader.reset();
    intReader.reset();
    addressReader.reset();
  }
}
