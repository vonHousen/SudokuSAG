package sudoku;

public class Memory
{
    /** Array of award values. The first index is for field and the second for digit. */
    private final float[][] _awards;
    /** Array of collisions. The first index is for field and the second for digit. If true, collision occurs. */
    private final boolean[][] _collisions;
    /** Vector of current sudoku digits */
    private final int[] _digitVector;
    /** Array of flags indicating hard-coded fields. If true, field cannot be modified. */
    private final boolean[] _mask;

    public Memory(int sudokuSize)
    {
        this._awards = new float[sudokuSize][sudokuSize]; // By default initialized to 0
        this._collisions = new boolean[sudokuSize][sudokuSize];
        this._digitVector = new int[sudokuSize];
        this._mask = new boolean[sudokuSize];
    }

    /**
     * Add reward for current combination of digits.
     * @param amount 	reward added for each non-empty, mutable sudoku field
     */
    public void rewardCurrentDigits(int amount)
    {
        for (int i = 0; i < _digitVector.length; ++i)
        {
            if (!_mask[i] && _digitVector[i] != 0)
            {
                _awards[i][_digitVector[i]-1] += amount;
            }
        }
    }

    /**
     * Set digit of mutable sudoku field.
     * This should be called after accepting a digit by the Table.
     * If the sudoku field is hard-coded (immutable), DigitImmutableException is thrown.
     * If the digit is out of range (0 is acceptable - means the field is empty), DigitOutOfRangeException is thrown.
     * @param n 	internal index of sudoku field
     * @param digit digit to be inserted
     */
    public void setDigit(int n, int digit)
    {
        if (_mask[n])
        {
            throw new Sudoku.DigitImmutableException("Digit is hard-coded and cannot be modified");
        }
        if (digit < 0 || digit > _digitVector.length)
        {
            throw new Sudoku.DigitOutOfRangeException("Sudoku digit out of range");
        }
        _digitVector[n] = digit;
    }

    /**
     * Set properties of a sudoku field. Should be called only at the initialization phase.
     * Sets both digit and mask of a field indexed internally.
     * @param n internal index of the field
     * @param digit digit value to be set
     * @param mask  mask value to be set
     */
    public void setField(int n, int digit, boolean mask)
    {
        if (digit < 0 || digit > _digitVector.length)
        {
            throw new Sudoku.DigitOutOfRangeException("Sudoku digit out of range");
        }
        _digitVector[n] = digit;
        _mask[n] = mask;
    }

    public int getDigit(int n)
    {
        return _digitVector[n];
    }

    public boolean getMask(int n)
    {
        return _mask[n];
    }

    public float getAward(int n, int digit)
    {
        return _awards[n][digit];
    }

    public boolean getCollision(int n, int digit) {return _collisions[n][digit]; }

    /**
     * Reset memory values that are not retained between iterations.
     * Should be called each time before starting solving sudoku (before every iteration).
     * Digit vector and mask have to be initialized for this to work.
     */
    public void reset()
    {
        final int sudokuSize = _digitVector.length;
        for (int i = 0; i < sudokuSize; ++i)
        {
            if (!_mask[i])
            {
                _digitVector[i] = 0;
            }
            for (int j = 0; j < sudokuSize; ++j)
            {
                _collisions[i][j] = _mask[i]; // Hard-coded fields (probably could just set it to false)
                if (_mask[i])
                {
                    _collisions[j][_digitVector[i]-1] = true;
                }
            }
        }
    }
}
