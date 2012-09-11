package water;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Arrays;

import water.LogHub.LogEvent;
import water.LogHub.LogSubscriber;
import water.web.ProgressReport;

/**
 * Local log subscriber which consumes all kinds of log events (local, remote) a publishes
 * them as an input stream. Typically the input stream is then published via HTTP REST API - see {@link ProgressReport}
 * 
 * @author michal
 * 
 * @see ProgressReport
 *
 */
public class LocalLogSubscriber implements LogSubscriber {

  // Mark live subscriber - can be accessed by different threads. 
  private volatile boolean isAlive = true;
  private Thread readerThread = null;
  
  private SubscriberPipe pipe = new SubscriberPipe();
  
  public LocalLogSubscriber() { this(false); }
  public LocalLogSubscriber(boolean encodeHtml) { pipe.sink.encodeHtml = encodeHtml; }
  
  @Override public void write(LogEvent event) {
    //System.err.println("Event received by local subscriber: " + event);
    try {
      pipe.sink.write(event);
    } catch( IOException e ) {
      // the stream does not seem alive => mark the subscriber dead and let LogHub to remove it
      isAlive = false;
    }
    if (readerThread!=null && !readerThread.isAlive()) isAlive = false;
  }
 
  @Override public boolean isLocal() { return true; }    
  @Override public boolean isAlive() { return isAlive; }
  @Override public boolean accept(final LogEvent e) { return true; } // Accept all events!
  
  public InputStream getInputStream() {
    // NOTE: this is NANOHttp-specific solution. If the connection is broken, NANOHttp silently finish response
    // thread. However, it forgets to close input stream filling response.
    // Hence, the solution is to remember the NANOHttp response thread and check if it is still alive.
    readerThread = Thread.currentThread();    
    return pipe.source; 
  }
  
  /**
   * A simple pipe between threads - multiple writers - single reader
   * 
   * Note: I do not want to use java.nio.Pipe due to it does not specify explicit
   * blocking policy. 
   * I do not want to block writers -- the implementation prefers to lost data.
   */
  public static class SubscriberPipe {
    
    public static final int INITIAL_SIZE = 512;
    public static final int MAX_SIZE = 32 * 1024; // 32kb buffer
            
    private byte[] buffer;
    private boolean availableData = false;
    private int writePosition = 0;
    private int countToBeRead = 0;
    private int readPosition  = -1;
    
    private Sink sink = new Sink();
    private Source source = new Source();
    
    public SubscriberPipe()         { this(INITIAL_SIZE); }    
    public SubscriberPipe(int size) { buffer = new byte[size]; }
    
    // Write to the buffer supports multiple writers
    // Buffer expands to its maximal size, then the round buffer is used.
    protected void write(int b) {
      synchronized( buffer ) {        
        // do not overwrite reader
        if (writePosition == buffer.length && buffer.length < MAX_SIZE) { // buffer can be resized
            buffer = Arrays.copyOf(buffer, Math.max(buffer.length << 1, writePosition));
        } 
        
        if (writePosition < buffer.length) { // DROP Data if the buffer is full
          buffer[writePosition++] = (byte) b;
          countToBeRead++;
          
          if (!availableData) {
            availableData = true;
            buffer.notify();
          }
        }
      }
    }
    
    protected int read() throws IOException {
      synchronized( buffer ) {
        // read buffer is empty or consumer already read all data
        if (readPosition == -1 || readPosition == writePosition) {
          availableData = false;
          readPosition = 0; writePosition = 0;
          while (!availableData)  {
            try {
              buffer.wait();
            } catch( InterruptedException e ) { e.printStackTrace(); }            
          }
        }     
        
        //System.err.println(new String(buffer, readPosition, writePosition-1));
        int result = buffer[readPosition++];            
        countToBeRead--;
        
        return result;
      }      
    }
    
    protected int available() {
      return (countToBeRead > 0 ? countToBeRead : 1); // prevent against inputstream close
    }
        
    public class Sink extends OutputStream {
      
      boolean encodeHtml = false;
            
      @Override public void write(int b) throws IOException {
        if (encodeHtml && HtmlCharacters.needsTranslation((char) b)) {
          HtmlCharacters htmlC = HtmlCharacters.valueOf((char) b);
          for(int i = 0; i < htmlC.htmlEntity.length; i++) {
            SubscriberPipe.this.write(htmlC.htmlEntity[i]);
          }
        } else {
          SubscriberPipe.this.write(b);                    
        }        
      }
      
      void write(LogEvent logEvent) throws IOException {
        write('['); write(logEvent.kind.label.getBytes()); write(']');
        write('['); write(logEvent.node.toString().getBytes()); write(']');
        write('['); write(logEvent.threadName.getBytes()); write("]: ".getBytes()); 
        write(logEvent.data, 0, logEvent.data.length);
        write('\n');
      }
    }
    
    public class Source extends InputStream {

      @Override public int read() throws IOException {
        return SubscriberPipe.this.read();
      } 
      
      @Override public int available() throws IOException {
        return SubscriberPipe.this.available();
      }      
    }
  }
  
  /**
   * Enum which allows for translating characters to HTML entities.
   */
  enum HtmlCharacters {
    AMP   ('&', new char[] {'&', 'a', 'm', 'p', ';'}), 
    QUOT  ('"', new char[] {'&', 'q', 'u', 'o', 't', ';'}),
    LT    ('<', new char[] {'&', 'l', 't', ';'}),
    GT    ('>', new char[] {'&', 'g', 't', ';'});
    
    char character;
    char[] htmlEntity;
    
    static HtmlCharacters valueOf(char c) {      
      for(HtmlCharacters hc : values()) {
        if (hc.character == c) return hc;                        
      }      
      return null;
    }
    static boolean needsTranslation(char c) { if (c == '&' || c == '"' || c == '<' || c == '>') return true; else return false; }
    private HtmlCharacters(char character, char[] htmlEntity) { this.character = character; this.htmlEntity = htmlEntity; }    
  }
}