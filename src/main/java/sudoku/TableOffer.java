package sudoku;

import java.util.ArrayList;

public class TableOffer
{
    /** Value of a digit offered by a Player. */
    public int _digit;
    /** Weights of the _digit given by different Players. Valid only when _weightFlags is true. */
    public final float[] _weights;
    /** Sum of _weights for the offered _digit. Valid only when _weightFlags are set to true for all Players. */
    public float _weightsSum;
    /** Flags indicating whether _weights value for the _digit is known for a specific Player. */
    public final boolean[] _weightFlags;

    public TableOffer(int digit)
    {
        this._digit = digit;
        this._weights = new float[3];
        this._weightFlags = new boolean[3]; // By default initialized to false
    }
}
