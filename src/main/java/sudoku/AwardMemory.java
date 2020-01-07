package sudoku;

// import sun.util.resources.cldr.si.CurrencyNames_si;

public class AwardMemory
{
    /** Array of award values. The first index is for field and the second for digit. */
    private final int[][] _awards;
    /** Vector of current sudoku digits */
    private final int[] _digitVector;
    /** Array of flags indicating hard-coded fields. If true, field cannot be modified. */
    private final boolean[] _mask;

    public AwardMemory(int[] digitVector, boolean[] mask)
    {
        this._awards = new int[digitVector.length][digitVector.length]; // By default initialized to 0
        this._digitVector = digitVector;
        this._mask = mask;
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

    public int getDigit(int n)
    {
        return _digitVector[n];
    }

    public int getAward(int n, int digit)
    {
        return _awards[n][digit];
    }
}
