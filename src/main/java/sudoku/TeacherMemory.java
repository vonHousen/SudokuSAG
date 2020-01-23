package sudoku;

public class TeacherMemory
{
    final private int _maxPlayerCount;

    final private int _maxTableCount;

    private int _maxTableFinishedCount;

    private int _playerRewardedCount;

    private int _playerResetCount;

    private int _tableResetCount;

    private int _tableFinishedCount;

    public TeacherMemory(int playerCount, int tableCount, int finishedCount)
    {
        this._maxPlayerCount = playerCount;
        this._maxTableCount = tableCount;
        this._maxTableFinishedCount = finishedCount;
        this._playerRewardedCount = 0;
        this._playerResetCount = 0;
        this._tableResetCount = 0;
        this._tableFinishedCount = 0;
    }

    public void setMaxTableFinishedCount(int count)
    {
        _maxTableFinishedCount = count;
    }

    private boolean allResetsCollected()
    {
        return _playerResetCount == _maxPlayerCount && _tableResetCount == _maxTableCount;
    }

    public boolean addPlayerRewarded()
    {
        ++_playerRewardedCount;
        return _playerRewardedCount == _maxPlayerCount;
    }

    public boolean addPlayerReset()
    {
        ++_playerResetCount;
        return allResetsCollected();
    }

    public boolean addTableReset()
    {
        ++_tableResetCount;
        return allResetsCollected();
    }

    public boolean addTableFinished()
    {
        ++_tableFinishedCount;
        return _tableFinishedCount == _maxTableFinishedCount;
    }

    void reset()
    {
        _playerRewardedCount = 0;
        _playerResetCount = 0;
        _tableResetCount = 0;
        _tableFinishedCount = 0;
    }
}
