package water;
/**
 * Empty marker interface.  Used by the auto-serializer to inject these calls:
 *   public AutoBuffer write(AutoBuffer bb) { ... }
 *   public <this>     read (AutoBuffer bb) { ... }
 */
interface Freezable extends Cloneable {
  public AutoBuffer write(AutoBuffer bb);
  public <T extends Freezable> T read(AutoBuffer bb);
}
