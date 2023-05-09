package fr.uge.ugegreed;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.SocketChannel;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Objects;
import java.util.logging.Logger;

import fr.uge.ugegreed.query.*;
import fr.uge.ugegreed.reader.*;

public class Context {
	
    private static final Charset UTF8 = StandardCharsets.UTF_8;
    private static int BUFFER_SIZE = 1024;
    private final SelectionKey key;
    private final SocketChannel sc;
    private final ByteBuffer bufferIn = ByteBuffer.allocate(BUFFER_SIZE);
    private ByteBuffer bufferOut = ByteBuffer.allocate(BUFFER_SIZE);
    private final ArrayDeque<Query> queryQueue = new ArrayDeque<>();
    private final ClientServer server;
    private InetSocketAddress id;
    private static final Logger logger = Logger.getLogger(Context.class.getName());
    private final Object bufferOutLock = new Object();
    
    private final QueryReader queryReader = new QueryReader();
    private boolean closed = false;
    private final Mode mode;
    
    public Context(ClientServer server, SelectionKey key, Mode mode) {
      this.key = key;
      this.sc = (SocketChannel) key.channel();
      this.server = server;
      this.mode = mode;
    }
    
    public Context(InetSocketAddress id, Mode mode) {
      this.id = id;
      this.key = null;
      this.sc = null;
      this.server = null;
      this.mode = mode;
    }
    
    public SelectionKey getKey() {
    	return key;
    }
    
    public InetSocketAddress getId() {
    	return id;
    }
    
    public void setID(InetSocketAddress id) {
      this.id = id;
    }
    
    public boolean isClosed() {
      return closed;
    }
    
    public Mode getMode() {
      return mode;
    }
    
    public void closePort() {
      closed = true;
    }
    
    /**
     * Process the content of bufferIn
     *
     * The convention is that bufferIn is in write-mode before the call to process and
     * after the call
     *
     */
    public void processIn() {
      for (;;) {
        Reader.ProcessStatus status = queryReader.process(bufferIn);
        switch (status) {
        case DONE:
          var value = queryReader.get();
          queryReader.reset();
    			try {
    			  synchronized(bufferOutLock) {
    			    server.processQuery(value, key);
    			  }
    			} catch (InterruptedException e) {
    				e.printStackTrace();
    			}
          return;
        case REFILL:
          return; 
        case ERROR:
          logger.warning("Client send wrong request so he will be ignored /!\\");
          return;
        }
      }
    }
    
    public int getNbByteAddress(InetSocketAddress address) {
      return (Integer.BYTES * 2) + StandardCharsets.UTF_8.encode(address.getHostName()).remaining();
    };
    
    public void processOutAddress(InetSocketAddress address) {
      if (bufferOut.remaining() < getNbByteAddress(address)) {
        throw new IllegalStateException("no enough space in buffer");
      }
	    var hostname = StandardCharsets.UTF_8.encode(address.getHostName());
	    var port = address.getPort();
	    bufferOut.putInt(hostname.remaining());
	    bufferOut.put(hostname);
	    bufferOut.putInt(port);
    }

    
    public boolean processQuery0(Query query) {
    	var queryf = (Query0) query;
        var calcId = queryf.getCalcId();
        var address = calcId.origin();
        var url = UTF8.encode(queryf.getUrl());
        var className = UTF8.encode(queryf.getClassName());
        if (bufferOut.remaining() < Integer.BYTES * 6 + getNbByteAddress(address) + url.remaining() + className.remaining()) {
          logger.warning("no enough space in buffer");
          return false;
        }
        bufferOut.putInt(query.id());
        // Add calcId
        bufferOut.putInt(calcId.id());
        processOutAddress(address);
        // Add url
        bufferOut.putInt(url.remaining());
        bufferOut.put(url);
        // Add className
        bufferOut.putInt(className.remaining());
        bufferOut.put(className);
        //Add start/end range
        bufferOut.putInt(queryf.getStartRange());
        bufferOut.putInt(queryf.getEndRange());
        
        return true;
    }
    
    public boolean processQuery1(Query query) {
    	var queryf = (Query1) query;
        var calcId = queryf.getCalcId();
        var address = calcId.origin();
        var accept = queryf.getAccept() ? 1 : 0;
        if(accept == 0) {
          var url = StandardCharsets.UTF_8.encode(queryf.getUrl());
          var className = StandardCharsets.UTF_8.encode(queryf.getClassName());
          var urlInt = url.remaining();
          var classNameInt = className.remaining();
          
          if (bufferOut.remaining() < Integer.BYTES * 7 + getNbByteAddress(address) + urlInt + classNameInt) {
            logger.warning("no enough space in buffer");
            return false;
          }
          bufferOut.putInt(query.id());
          // Add calcId
          bufferOut.putInt(calcId.id());
          processOutAddress(address);
          // Add accept
          bufferOut.putInt(accept);
          // Add url
          bufferOut.putInt(urlInt);
          bufferOut.put(url);
          // Add className
          bufferOut.putInt(classNameInt);
          bufferOut.put(className);
	        // Add start/end range
	        bufferOut.putInt(queryf.getStartRange());
	        bufferOut.putInt(queryf.getEndRange());
        } else {
          if (bufferOut.remaining() < Integer.BYTES * 3 + getNbByteAddress(address)) {
            logger.warning("no enough space in buffer");
            return false;
          }
          bufferOut.putInt(query.id());
          // Add calcId
          bufferOut.putInt(calcId.id());
          processOutAddress(address);
          // Add accept
          bufferOut.putInt(accept);
        }
        
        return true;
    }
    
    public boolean processQuery2(Query query) {
    	var queryf = (Query2) query;
        var calcId = queryf.getCalcId();
        var address = calcId.origin();
        var value = queryf.getValue();
        var res = UTF8.encode(queryf.getRes());
        
        var capacite = (Integer.BYTES * 4) + getNbByteAddress(address) + res.remaining();
        if (bufferOut.remaining() < capacite) {
          logger.warning("no enough space in buffer");
          return false;
        }
        
        bufferOut.putInt(query.id());
        // Add calcId
        bufferOut.putInt(calcId.id());
        processOutAddress(address);
        // Add value
        bufferOut.putInt(value);
        // Add res
        bufferOut.putInt(res.remaining());
        bufferOut.put(res);
        
        return true;
    }
    
    public boolean processQuery3(Query query) {
        var queryf = (Query3) query;
        var rep = queryf.getReponse();
        Byte refus = null;
        InetSocketAddress addressDeco = null;
        ArrayList<InetSocketAddress> nodes = null;
        int taille = 0;
        int nbByteList = 0;
        
        if (rep == 1) {
          refus = queryf.getRefus();
        } else {
          addressDeco = queryf.getNodeDeco();
          
          nodes = queryf.getNodes();
          taille = nodes.size();
          
          for (var i = 0; i < taille; i++) {
            var address = nodes.get(i);
            nbByteList += getNbByteAddress(address);
          }
        }
        
        if (rep == 1) {
          if (bufferOut.remaining() < Integer.BYTES + Byte.BYTES * 2) {
            logger.warning("no enough space in buffer");
            return false;
          }
          bufferOut.putInt(query.id());
          bufferOut.put(rep);
          bufferOut.put(refus);
        } else {
          if (bufferOut.remaining() < Integer.BYTES * 2 + Byte.BYTES + getNbByteAddress(addressDeco) + nbByteList) {
            logger.warning("no enough space in buffer");
            return false;
          }
          bufferOut.putInt(query.id());
          bufferOut.put(rep);
          processOutAddress(addressDeco);
          bufferOut.putInt(taille);
          for (var i = 0; i < taille; i++) {
            var address = nodes.get(i);
            processOutAddress(address);
          }
        }
        
        return true;
    }
    
    public boolean processQuery4 (Query query) {
        var queryf = (Query4) query;
        var address = queryf.getNewDest();
        if(bufferOut.remaining() < Integer.BYTES + getNbByteAddress(address)) {
          logger.warning("no enough space in buffer");
          return false;
        }
        bufferOut.putInt(query.id());
        processOutAddress(address);
        return true;
    }
    
    public boolean processQuery6(Query query) {
    	var queryf = (Query6) query;
        var fa = queryf.getForAll();
        
        var address = queryf.getNodeOrigin();
        InetSocketAddress addressDest = null;
        if (fa == 1) {
          // Add dest
          addressDest = queryf.getNodeDest();
          
          if(bufferOut.remaining() < Integer.BYTES + Byte.BYTES + getNbByteAddress(addressDest) + getNbByteAddress(address)) {
            logger.warning("no enough space in buffer");
            return false;
          }
        } else if(bufferOut.remaining() < Integer.BYTES +  Byte.BYTES + getNbByteAddress(address)) {
          logger.warning("no enough space in buffer");
          return false;
        }
        bufferOut.putInt(query.id());
        bufferOut.put(fa);
        processOutAddress(address);
        
        if (fa == 1) {	//Not Root
          // Add dest
          processOutAddress(addressDest);
        }
        
        return true;
    }
    
    public boolean processQuery7(Query query) {
    	var queryf = (Query7) query;
        var a = queryf.getAccept();
        
        InetSocketAddress nodeOrigin = null;
        ArrayList<InetSocketAddress> nodeOriginList = null;
        int taille = 0;
        int nbByteList = 0;
        // 0 refus sinon :
        if (a == 1)  {
          //encodage du premier noeud
          nodeOrigin = queryf.getNodeOriginList();
          
          //encodage du rooter (liste de node)
          nodeOriginList = queryf.getRooter();
          taille = nodeOriginList.size();
          
          for (var i = 0; i < taille; i++) {
            var address = nodeOriginList.get(i);
            nbByteList += getNbByteAddress(address);
          }
        }
        
        if (a == 0) {
          if (bufferOut.remaining() < Integer.BYTES + Byte.BYTES) {
            logger.warning("no enough space in buffer");
            return false;
          }
          bufferOut.putInt(query.id());
          bufferOut.put(a);
        } else {
          if (bufferOut.remaining() < Integer.BYTES * 2 + Byte.BYTES + getNbByteAddress(nodeOrigin) + nbByteList) {
            logger.warning("no enough space in buffer");
            return false;
          }
          bufferOut.putInt(query.id());
          bufferOut.put(a);
          processOutAddress(nodeOrigin);
          bufferOut.putInt(taille);
          for (var i = 0; i < taille; i++) {
            var address = nodeOriginList.get(i);
            processOutAddress(address);
          }
        }
        
        return true;
    }
    
    public boolean processQuery8(Query query) {
        var queryf = (Query8) query;
        var addressFather = queryf.getNodeFather();
        var addressDeleted = queryf.getNodeDeleted();
        
        if(bufferOut.remaining() < Integer.BYTES + Byte.BYTES + getNbByteAddress(addressFather) + getNbByteAddress(addressDeleted)) {
          logger.warning("no enough space in buffer");
          return false;
        }
        bufferOut.putInt(query.id());
        processOutAddress(addressFather);
        processOutAddress(addressDeleted);
        
        return true;
    }
    
    private boolean processQuery(Query query) {
      Objects.requireNonNull(query);
      switch(query.id()) {
        case 0 -> {
          return processQuery0(query);
        }
        case 1 -> {
          return processQuery1(query);
        }
        case 2 -> {
          return processQuery2(query);
        }
        case 3 -> {
          return processQuery3(query);
        }
        case 4 -> {
          return processQuery4(query);
        }
        case 6-> {
          return processQuery6(query);
        }
        case 7 -> {
          return processQuery7(query); 
        }
        case 8 -> {
          return processQuery8(query); 
        }
        
      }
      return true;
    }
    
    /**
     * Try to fill bufferOut from the query queue
     *
     */
    public void processOut() {
      var query = queryQueue.poll();
      if(query == null) {
        logger.info("no query in the queue");
        return;
      }
      while (query != null) {
        synchronized(bufferOutLock) {
          while (!processQuery(query)) {
            //System.out.println("agrandissement");
            resizeBufferOut();
          };
        }
        query = queryQueue.poll();
      }
      
    }
    /**
     * Update the interestOps of the key looking only at values of the boolean
     * closed and of both ByteBuffers.
     *
     * The convention is that both buffers are in write-mode before the call to
     * updateInterestOps and after the call. Also it is assumed that process has
     * been be called just before updateInterestOps.
     * @throws IOException 
     */

    public void updateInterestOps() {
      var interestOps = 0;
      
      if (bufferIn.hasRemaining() && !closed) {
        interestOps |= SelectionKey.OP_READ;
      }
      if (bufferOut.position() != 0) {
        interestOps |= SelectionKey.OP_WRITE;
      }
      if (interestOps == 0) {
        silentlyClose();
        return;
      }
      
      key.interestOps(interestOps);
    }

    public void silentlyClose() {
      try {
        sc.close();
      } catch (IOException e) {
        // ignore exception
      }
    }

    /**
     * Performs the read action on sc
     *
     * The convention is that both buffers are in write-mode before the call to
     * doRead and after the call
     *
     * @throws IOException
     */
    public void doRead() throws IOException {
      // TODO
      if(sc.read(bufferIn)==-1) {
        closed = true;
        return;
      }
      processIn();
      updateInterestOps();
    }

    /**
     * Performs the write action on sc
     *
     * The convention is that both buffers are in write-mode before the call to
     * doWrite and after the call
     *
     * @throws IOException
     */

    public void doWrite() throws IOException {
      bufferOut.flip();
      sc.write(bufferOut);
      bufferOut.compact();
      updateInterestOps();
    }
    
    public boolean doConnect() throws IOException {
	    if (mode != Mode.NORMAL) {
	      throw new IllegalStateException();
	    }
	    if (!sc.finishConnect())
	        return false; // the selector gave a bad hint
	    updateInterestOps();
	    key.interestOps(SelectionKey.OP_READ);
	    
	    var context = (Context) key.attachment();
	    server.getRooter().update(context, context);
	    addNewNode(server.getID());;    
	    return true;
    }
    
    /**
     * double la taille du bufferOut
     */
    private void resizeBufferOut() {
      logger.info("Resizing of the bufferOut");
      var pos = bufferOut.position();
      BUFFER_SIZE *= 2;
      var bufferTmp = ByteBuffer.allocate(BUFFER_SIZE);
      bufferTmp.put(bufferOut);
      bufferOut = bufferTmp;
      bufferOut.position(pos);
    }
    
    /**
     * Ajoute une requete à la queryQueue
     * @param newQuery {@link Query} à ajouter
     */
    public void addNewQuery(Query newQuery) {
      queryQueue.add(newQuery);
      if(bufferOut.hasRemaining()) {
        processOut();
      } else {
        resizeBufferOut();
        processOut();
      }
      updateInterestOps();
    }
    

    /**
     * Crée une requete (id3) qui indique qu'un noeud se deco
     * 
     * @param nodeDeco {@link InetSocketAddress} noeud qui envoit la requête
     * @param fils {@link ArrayList<InetSocketAddress>} liste d'address de tout les fils du noeud
     * */
    public void addDeco(InetSocketAddress nodeDeco, ArrayList<InetSocketAddress> fils) {
      var newQuery = new Query3(nodeDeco, fils);
      addNewQuery(newQuery);
    }
    
    /**
     * Crée une requete (id3) qui autorise une deconexion
     * 
     * @param refus {@link Byte} 0 ou 1 
     * */
    public void addRefusDeco(Byte refus) {
      var newQuery = new Query3(refus);
      addNewQuery(newQuery);
    }

    /**
     * Crée une requete (id4) qui redirige la connexion du noeud 
     * 
     * @param address {@link InetSocketAddress} du nouveau père 
     * */
    public void addReco(InetSocketAddress address) {
     var newQuery = new Query4(address);
     addNewQuery(newQuery);
    }
    
    /**
     * Crée une requete (id6) qui contient l'InetSocketAddress d'un nouveau neoud dans le reseau
     * et l'envoie dans la queryQueue
     * 
     * @param nodeOrigin l'{@link InetSocketAddress} du nouveau neoud dans le reseau.
     * */
    public void addNewNode(InetSocketAddress nodeOrigin) {
      var newQuery = new Query6(nodeOrigin);
      addNewQuery(newQuery);
    }
    
    /**
     * Crée une requete (id6) qui contient l'InetSocketAddress d'un nouveau neoud dans le reseau
     * et l'envoie dans la queryQueue
     * 
     * @param nodeOrigin l'{@link InetSocketAddress} du nouveau neoud dans le reseau.
     * @param nodeDest l'{@link InetSocketAddress} du noeud le plus proche de nodeOrigin
     * */
    public void addNewNode(InetSocketAddress nodeOrigin, InetSocketAddress nodeDest) {
      var newQuery = new Query6(nodeOrigin, nodeDest);
      addNewQuery(newQuery);
    }

    
    /**
     * Crée une requete (id7) qui indique si une connexion est accepté ou non
     * (spoiler non sinon IllegalArgumentException)
     * 
     * @param accept {@link Byte} 0 ou 1 
     * */
    public void addAccept(Byte accept) {
      var newQuery = new Query7(accept);
      addNewQuery(newQuery);
    }

    
    /**
     * Crée une requete (id7) qui indique si une connexion est accepté ou non
     * (spoiler oui)
     * Avec une liste d'address d'un rooter à accepter relatif à un noeud
     * 
     * @param nodeOrigin {@link InetSocketAddress}  noeud qui envoit la requête
     * @param listAddress liste d'address du rooter relatif à nodeOrigin
     * */
    public void addRooter(InetSocketAddress nodeOrigin, ArrayList<InetSocketAddress> listAddress) {
      var newQuery = new Query7(nodeOrigin, listAddress);
      addNewQuery(newQuery);
    }

    /**
     * Crée une requete (id8) qui indique qu'un noeud dans le reseau est supprimé dans le réseau et doit
     * être remplacé par son noeud père
     * 
     * @param nodeFather l'{@link InetSocketAddress} du noeud parent.
     * @param nodeDeleted l'{@link InetSocketAddress} du noeud supprimé dans le reseau.
     */
    public void addDeletedNode(InetSocketAddress nodeFather, InetSocketAddress nodeDeleted) {
      var newQuery = new Query8(nodeFather, nodeDeleted);
      addNewQuery(newQuery);
    }
    
}
