package tinygit;

/** General exception indicating a Gitlet error.  For fatal errors, the
 *  result of .getMessage() is the error message to be printed.
 *  @author P. N. Hilfinger
 */
class TinyGitException extends RuntimeException {


    /** A GitletException with no message. */
    TinyGitException() {
        super();
    }

    /** A GitletException MSG as its message. */
    TinyGitException(String msg) {
        super(msg);
    }

}
