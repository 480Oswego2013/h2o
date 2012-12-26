package water;
import java.io.IOException;
import java.net.DatagramPacket;

/**
 * A UDP Rebooted packet: this node recently rebooted
 *
 * @author <a href="mailto:cliffc@0xdata.com"></a>
 * @version 1.0
 */

public class UDPRebooted extends UDP {
  public static enum T {
    none,
    reboot,
    shutdown,
    error,
    locked,
    mismatch;

    public void send(H2ONode target) {
      assert this != none;
      new AutoBuffer(target).putUdp(udp.rebooted).put1(ordinal()).close();
    }
    public void broadcast() { send(H2O.SELF); }
  }

  public static void checkForSuicide(int first_byte, AutoBuffer ab) {
    if( first_byte != UDP.udp.rebooted.ordinal() ) return;
    int type = ab.get1();
    suicide( T.values()[type], ab._h2o);
  }

  public static void suicide( T cause, H2ONode killer ) {
    String m;
    switch( cause ) {
    case none:   return;
    case reboot: return;
    case shutdown:
      closeAll();
      System.out.println("[h2o] Orderly shutdown command from "+killer);
      System.exit(0);
      return;
    case error:    m = "Error leading to a cloud kill"              ; break;
    case locked:   m = "Killed joining a locked cloud"              ; break;
    case mismatch: m = "Killed joining a cloud with a different jar"; break;
    default:       m = "Received kill "+cause                       ; break;
    }
    closeAll();
    System.err.println("[h2o] "+m+" from "+killer);
    System.exit(-1);
  }

  AutoBuffer call(AutoBuffer ab) {
    if( ab._h2o != null ) ab._h2o.rebooted();
    return ab;
  }

  // Try to gracefully close/shutdown all i/o channels.
  public static void closeAll() {
    try { H2O._webSocket.close(); } catch( IOException x ) { }
    try { H2O._udpSocket.close(); } catch( IOException x ) { }
    try { H2O._apiSocket.close(); } catch( IOException x ) { }
    try { TCPReceiverThread.SOCK.close(); } catch( IOException x ) { }
  }
}
