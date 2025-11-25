/** Unchecked exception that should never ever get handled in same lexical scope &amp; treated like Rust's-Golang's {@code panic!()}.<br/>
  * Like the name suggest, any time this get thrown, it can be assumed there's bug in the code.<br/>
  * Again, like Rust &amp; Golang community do, use this sparingly. */
public final class BuggyCodeException extends RuntimeException {
    public BuggyCodeException(String invariantAssumptionMessage) {
        super(invariantAssumptionMessage);
    }

    public BuggyCodeException(String invariantAssumptionMessage, Throwable throwable) {
        super(invariantAssumptionMessage, throwable);
    }
}
