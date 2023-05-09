package fr.uge.ugegreed.reader;

import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.util.ArrayList;

import fr.uge.ugegreed.query.Query3;

public class Query3Reader implements Reader<Query3> {
  
  private enum State {
    DONE,
    WAITING_FOR_REPONSE,
    WAITING_FOR_REFUS,
    WAITING_FOR_ADRESS_DECO, WAITING_FOR_SIZE, WAITING_FOR_NODES,
    ERROR
  };
  
  private State state = State.WAITING_FOR_REPONSE;
  private ByteReader byteReader = new ByteReader();
  private IntReader intReader = new IntReader();
  private AddressReader addressReader = new AddressReader();
  private Query3 query;
  
  private InetSocketAddress addressdeco = null;
  private ArrayList<InetSocketAddress> nodes = new ArrayList<InetSocketAddress>();
  private int nbNodes;
  private int numNode;
  
  @SuppressWarnings("incomplete-switch")
  @Override
  public ProcessStatus process(ByteBuffer bb) {
    if (state == State.DONE || state == State.ERROR) {
      throw new IllegalStateException();
    }
    InetSocketAddress node = null;
    for (;;) {
      switch(state) {
      case WAITING_FOR_REPONSE:
        var byteReaderState = byteReader.process(bb);
        switch (byteReaderState) {
          case REFILL:
            System.out.println("refill reponse (byteReader)");  
            return ProcessStatus.REFILL;
          case ERROR:
            System.out.println("erreur reponse (byteReader)");
            return ProcessStatus.ERROR;
          default:
            break;
        }
        var reponse = byteReader.get();
        byteReader.reset();
        if (reponse == 0) {
          state = State.WAITING_FOR_ADRESS_DECO;
        } else {
          state = State.WAITING_FOR_REFUS;
        }
        continue;
      case WAITING_FOR_REFUS:
        byteReaderState = byteReader.process(bb);
        switch (byteReaderState) {
          case REFILL:
            System.out.println("refill refus (byteReader)");  
            return ProcessStatus.REFILL;
          case ERROR:
            System.out.println("erreur refus (byteReader)");
            return ProcessStatus.ERROR;
          default:
            break;
        }
        var refus = byteReader.get();
        byteReader.reset();
        query = new Query3(refus);
        state = State.DONE;
        continue;
      case WAITING_FOR_ADRESS_DECO:
        var addressReaderState = addressReader.process(bb);
        switch (addressReaderState) {
          case REFILL:
            System.out.println("refill deco (addressReader)");  
            return ProcessStatus.REFILL;
          case ERROR:
            System.out.println("erreur deco (addressReader)");
            return ProcessStatus.ERROR;
          default:
            break;
        }
        addressdeco = addressReader.get();
        addressReader.reset();
        state = State.WAITING_FOR_SIZE;
        continue;
      case WAITING_FOR_SIZE:
        var intReaderState = intReader.process(bb);
        switch (intReaderState) {
          case REFILL:
            System.out.println("refill size (intReader)");  
            return ProcessStatus.REFILL;
          case ERROR:
            System.out.println("erreur size (intReader)");
            return ProcessStatus.ERROR;
          default:
            break;
        }
        nbNodes = intReader.get();
        intReader.reset();
        state = State.WAITING_FOR_NODES;
        continue;
      case WAITING_FOR_NODES:
        if (numNode < nbNodes) {
          //System.out.println("SALUT");
          addressReaderState = addressReader.process(bb);
          switch (addressReaderState) {
            case REFILL:
              System.out.println("refill Listaddress node "+ numNode +" (addressReader)");
              return ProcessStatus.REFILL;
            case ERROR:
              System.out.println("erreur ListAddress (addressReader)");
              return ProcessStatus.ERROR;
            default:
              break;
          }
          node = addressReader.get();
          addressReader.reset();
          nodes.add(node);
          numNode++;
          continue;
        }
        query = new Query3(addressdeco, nodes);
        state = State.DONE;
        continue;
      case DONE:
        return ProcessStatus.DONE;
      }
    }
  }
  
  @Override
  public Query3 get() {
    if (state != State.DONE) {
      throw new IllegalStateException();
    }
    return query;
  }

  @Override
  public void reset() {
    state = State.WAITING_FOR_REPONSE;
    byteReader.reset();
    intReader.reset();
    addressReader.reset();
  }
}
