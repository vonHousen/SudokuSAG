package sudoku;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;

public class PlayerMemory
{
    /** Array of reward values. The first index is for field and the second for digit. */
    private final float[][] _rewards;
    /** Array of collisions. The first index is for field and the second for digit. If true, collision occurs. */
    private final boolean[][] _collisions;
    /** Vector of current sudoku digits */
    private final int[] _digitVector;
    /** Array of flags indicating hard-coded fields. If true, field cannot be modified. */
    private final boolean[] _mask;
    /** Array of digits ordered from highest to lowest priority for a specific Table. The first index is for field and the second for priority. */
    private final int[][] _digitPriorities;
    /** Array of Table internal indices ordered from highest to lowest priority. */
    private final int[] _tablePriorities;
    /** Array of flags indicating that Player accepted offer on a Table. */
    private final boolean[] _accepted;
    /** Array of flags indicating that a Table linked to a certain field ended negotiations. */
    private final boolean[] _finished;

    private static class WeightValuePair implements Comparable<WeightValuePair>
    {
        public float _weight;
        public int _value;

        public WeightValuePair(float weight, int value)
        {
            this._weight = weight;
            this._value = value;
        }

        public int compareTo(WeightValuePair p)
        {
            return Float.compare(_weight, p._weight);
        }
    }

    public PlayerMemory(int sudokuSize)
    {
        this._rewards = new float[sudokuSize][sudokuSize];              // By default initialized to 0
        this._collisions = new boolean[sudokuSize][sudokuSize];
        this._digitVector = new int[sudokuSize];                        // By default initialized to 0
        this._mask = new boolean[sudokuSize];                           // By default initialized to false
        this._digitPriorities = new int[sudokuSize][sudokuSize];
        this._tablePriorities = new int[sudokuSize];
        this._accepted = new boolean[sudokuSize];                       // By default initialized to false
        this._finished = new boolean[sudokuSize];                       // By default initialized to false
    }

    public int getSudokuSize() {return _digitVector.length;}

    public boolean isAccepted(int n) {return _accepted[n];}

    public void setAccepted(int n, boolean value) {_accepted[n] = value;}

    public boolean isFinished(int n) {return _finished[n];}

    public void finish(int n)
    {
        _finished[n] = true;
    }

    public ArrayList<Integer> finishNegotiations(int n)
    {
        _finished[n] = true;
        _accepted[n] = false;
        final ArrayList<Integer> tableIndices = new ArrayList<>();
        final int resultingDigit = _digitVector[n];
        for (int i = 0; i < _digitVector.length; ++i)
        {
            // If the digit was offered on another Table
            if (_digitVector[i] == resultingDigit)
            {
                tableIndices.add(i);
                _digitVector[i] = 0;
                _accepted[i] = false;
            }
        }
        return tableIndices;
    }

    public boolean alreadyAccepted(int digit)
    {
        for (int i = 0; i < _digitVector.length; ++i)
        {
            if (_digitVector[i] == digit && (_accepted[i] || _finished[i]))
            {
                return true;
            }
        }
        return false;
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
                _rewards[i][_digitVector[i]-1] += amount;
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

    public int getDigit(int n){return _digitVector[n];}

    public boolean getMask(int n) {return _mask[n];}

    public float getAward(int n, int digit) {return _rewards[n][digit-1];}

    public boolean getCollision(int n, int digit) {return _collisions[n][digit-1];}

    public void setCollision(int n, int digit) {_collisions[n][digit-1] = true;}

    public void setDigitColliding(int digit)
    {
        --digit;
        final int sudokuSize = _digitVector.length;
        for (int i = 0; i < sudokuSize; ++i)
        {
            _collisions[i][digit] = true;
        }
    }

    public int getTablePriority(int p){return _tablePriorities[p];}

    public int getDigitPriority(int n, int p){return _digitPriorities[n][p];}

    public void prioritizeTables()
    {
        final int sudokuSize = _digitVector.length;
        for (int i = 0; i < sudokuSize; ++i) // Sort digits by weight for each Table
        {
            final WeightValuePair[] digitWeightPair = new WeightValuePair[sudokuSize];
            for (int j = 0; j < sudokuSize; ++j)
            {
                digitWeightPair[j] = new WeightValuePair(_rewards[i][j], j + 1);
            }
            // Sort from highest to lowest
            Arrays.sort(digitWeightPair, Collections.reverseOrder());
            for (int j = 0; j < sudokuSize; ++j)
            {
                _digitPriorities[i][j] = digitWeightPair[j]._value;
            }
        }

        final WeightValuePair[] indexWeightPair = new WeightValuePair[sudokuSize];
        for (int i = 0; i < sudokuSize; ++i) // Sum weights for each Table
        {
            indexWeightPair[i] = new WeightValuePair(0, i);
            for (int j = 0; j < sudokuSize; ++j)
            {
                indexWeightPair[i]._weight += _rewards[i][j];
            }
        }
        // Sort tables from lowest to highest
        Arrays.sort(indexWeightPair);
        for (int i = 0; i < sudokuSize; ++i)
        {
            _tablePriorities[i] = indexWeightPair[i]._value;
        }
    }

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
            for (int j = 0; j < sudokuSize; ++j)
            {
                _collisions[i][j] = _mask[i];
            }
            if (_mask[i])
            {
                setDigitColliding(_digitVector[i]);
            }
            else
            {
                _digitVector[i] = 0;
            }
            _accepted[i] = false;
            _finished[i] = _mask[i];
        }
    }
}
