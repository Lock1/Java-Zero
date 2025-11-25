/** Unchecked exception that should never ever get handled in same lexical scope &amp; treated like Rust's-Golang's {@code panic!()}.<br/>
  * Like the name suggest, any time this get thrown, it can be assumed there's bug in the code.<br/>
  * Again, like Rust &amp; Golang community do, use this sparingly. We don't want this to be worthless like other exceptions.<br/><br/>
  *
  * Note: This unchecked exception deliberately only provide 2 constructors and all of them demands explanation on why this get thrown. */
public final class BuggyCodeException extends RuntimeException {
    /** Primary constructor with assumption message.
      * @param invariantAssumptionMessage Assumption message of why this exception should never get thrown in the 1st place */
    public BuggyCodeException(String invariantAssumptionMessage) {
        super(invariantAssumptionMessage);
    }

    /** Alternative rethrowing constructor.
      * @param invariantAssumptionMessage Assumption message of why this exception should never get thrown in the 1st place
      * @param throwable Throwable to be wrapped */
    public BuggyCodeException(String invariantAssumptionMessage, Throwable throwable) {
        super(invariantAssumptionMessage, throwable);
    }
}
