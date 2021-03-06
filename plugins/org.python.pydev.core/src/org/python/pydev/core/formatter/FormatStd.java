package org.python.pydev.core.formatter;

/**
 * Class that defines the format standard to be used
 *
 * @author Fabio
 */
public class FormatStd {

    /**
     * Format with autopep8.py?
     */
    public boolean formatWithAutopep8;

    /**
     * Parameters for autopep8.
     */
    public String autopep8Parameters;

    /**
     * Defines whether spaces should be added after a comma
     */
    public boolean spaceAfterComma;

    /**
     * Defines whether ( and ) should have spaces
     */
    public boolean parametersWithSpace;

    /**
     * Defines whether = should be spaces surrounded when inside of a parens (function call)
     * (as well as others related: *= +=, -=, !=, ==, etc).
     */
    public boolean assignWithSpaceInsideParens;

    /**
     * Defines whether operators should be spaces surrounded:
     * + - * / // ** | & ^ ~ =
     */
    public boolean operatorsWithSpace;

    public boolean addNewLineAtEndOfFile;

    public boolean trimLines;

    public boolean trimMultilineLiterals;

    // Only valid if manageBlankLines == true
    public int blankLinesTopLevel = 2;

    // Only valid if manageBlankLines == true
    public int blankLinesInner = 1;

    public boolean manageBlankLines = false;

    public static final int DONT_HANDLE_SPACES = -1;
    /**
     * -1 = don't handle
     * 0 = 0 space
     * 1 = 1 space
     * 2 = 2 spaces
     * ...
     */
    public int spacesBeforeComment = DONT_HANDLE_SPACES;

    /**
     * Spaces after the '#' in a comment. -1 = don't handle.
     */
    public int spacesInStartComment = DONT_HANDLE_SPACES;

    /**
     * This method should be called after all related attributes are set when autopep8 is set to true.
     */
    public void updateAutopep8() {
        if (formatWithAutopep8) {
            spaceAfterComma = true;
            parametersWithSpace = false;
            assignWithSpaceInsideParens = false;
            operatorsWithSpace = true;
            addNewLineAtEndOfFile = true;
            trimLines = true;
            trimMultilineLiterals = false;
            spacesBeforeComment = 2;
            spacesInStartComment = 1;
            manageBlankLines = true;
            blankLinesTopLevel = 2;
            blankLinesInner = 1;
        }
    }
}