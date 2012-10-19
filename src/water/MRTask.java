package water;
import jsr166y.CountedCompleter;

// Map/Reduce - style distributed computation

// @author <a href="mailto:cliffc@0xdata.com"></a>
public abstract class MRTask extends DRemoteTask {

  transient private int _lo, _hi; // Range of keys to work on
  transient private MRTask _left, _rite; // In-progress execution tree

  public void init() {
    _lo = 0;
    _hi = _keys.length;
  }

  // Run some useful function over this *local* key, and record the results in
  // the 'this' MRTask.
  abstract public void map( Key key );

  // Do all the keys in the list associated with this Node.  Roll up the
  // results into *this* MRTask.
  @Override
  public final void compute() {
    if( _hi-_lo >= 2 ) { // Multi-key case: just divide-and-conquer down to 1 key
      final int mid = (_lo+_hi)>>>1; // Mid-point
      assert _left == null && _rite == null;
      MRTask l = (MRTask)clone2(); // Clone by any other name
      MRTask r = (MRTask)clone2(); // Clone by any other name
      _left = l;
      _rite = r;
      _left._hi = mid;          // Reset mid-point
      _rite._lo = mid;          // Also set self mid-point
      setPendingCount(2);       // Two more pending forks
      _left.fork();             // Runs in another thread/FJ instance
      _rite.fork();             // Runs in another thread/FJ instance
    } else {
      if( _hi > _lo )           // Single key?
        map(_keys[_lo]);        // Get it, run it locally
    }
    tryComplete();              // And this task is complete
  }

  @Override public final void onCompletion( CountedCompleter caller ) {
    // Reduce results into 'this' so they collapse going up the execution tree.
    // NULL out child-references so we don't accidentally keep large subtrees
    // alive: each one may be holding large partial results.
    if( _left != null ) reduce_merge_pending(_left); _left = null;
    if( _rite != null ) reduce_merge_pending(_rite); _rite = null;
  }
}
