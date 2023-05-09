package fr.uge.ugegreed.reader;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;


public class StringReader implements Reader<String> {

    private enum State {
        DONE, WAITING_FOR_SIZE, WAITING_FOR_CONTENT, ERROR
    };

    private State state = State.WAITING_FOR_SIZE;
    private int BUFFER_SIZE = 1024;
    //private final ByteBuffer internalBufferSize = ByteBuffer.allocate(Integer.BYTES);
    private IntReader intReader = new IntReader();
    private final ByteBuffer internalBufferTexte = ByteBuffer.allocate(BUFFER_SIZE);
    private int size;
    private String texte;
    

    @SuppressWarnings("incomplete-switch")
    @Override
    public ProcessStatus process(ByteBuffer buffer) {
        if (state == State.DONE || state == State.ERROR) {
            throw new IllegalStateException();
        }
        
        try {
          for (;;) {
            switch(state) {
              case WAITING_FOR_SIZE:
                var intReaderState = intReader.process(buffer);
                switch (intReaderState) {
                  case REFILL:
                      System.out.println("refill size (intReader)");
                      return ProcessStatus.REFILL;
                  case ERROR:
                    return ProcessStatus.ERROR;
                }
                state = State.WAITING_FOR_CONTENT;
                continue;
              case WAITING_FOR_CONTENT:
                buffer.flip();
                size = intReader.get();
                if (size < 0) {
                  //System.out.println("Error : size neg");
                  state = State.ERROR;
                  return ProcessStatus.ERROR;
                }
              
                if (size > BUFFER_SIZE) {
                  //System.out.println("Error : size too big");
                  state = State.ERROR;
                  return ProcessStatus.ERROR;
                }
                
                internalBufferTexte.limit(size);
                if (buffer.remaining() <= internalBufferTexte.remaining()) {
                  internalBufferTexte.put(buffer);
                } else {
                  var oldLimit = buffer.limit();
                  buffer.limit(internalBufferTexte.remaining());
                  internalBufferTexte.put(buffer);
                  buffer.limit(oldLimit);
                }
                
                buffer.compact();
                if (internalBufferTexte.hasRemaining()) {
                  //System.out.println("refill in stringReader");
                  return ProcessStatus.REFILL;
                }
                state = State.DONE;
                continue;
              case DONE:
                internalBufferTexte.flip();
                var oldlimit = internalBufferTexte.limit();
                internalBufferTexte.limit(internalBufferTexte.position() + size);
                texte = StandardCharsets.UTF_8.decode(internalBufferTexte).toString();
                internalBufferTexte.limit(oldlimit);
                return ProcessStatus.DONE;
                
            }
          }
            
        } finally {
            
        }
        
    }

    @Override
    public String get() {
        if (state != State.DONE) {
            throw new IllegalStateException();
        }
        return texte;
    }

    @Override
    public void reset() {
        state = State.WAITING_FOR_SIZE;
        intReader.reset();
        internalBufferTexte.clear();
    }
}