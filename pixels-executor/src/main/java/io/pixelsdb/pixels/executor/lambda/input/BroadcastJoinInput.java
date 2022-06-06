/*
 * Copyright 2022 PixelsDB.
 *
 * This file is part of Pixels.
 *
 * Pixels is free software: you can redistribute it and/or modify
 * it under the terms of the Affero GNU General Public License as
 * published by the Free Software Foundation, either version 3 of
 * the License, or (at your option) any later version.
 *
 * Pixels is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * Affero GNU General Public License for more details.
 *
 * You should have received a copy of the Affero GNU General Public
 * License along with Pixels.  If not, see
 * <https://www.gnu.org/licenses/>.
 */
package io.pixelsdb.pixels.executor.lambda.input;

import io.pixelsdb.pixels.executor.lambda.domain.BroadCastJoinTableInfo;
import io.pixelsdb.pixels.executor.lambda.domain.JoinInfo;
import io.pixelsdb.pixels.executor.lambda.domain.MultiOutputInfo;

/**
 * @author hank
 * @date 07/05/2022
 */
public class BroadcastJoinInput implements JoinInput
{
    /**
     * The unique id of the query.
     */
    private long queryId;

    /**
     * The small and broadcast table.
     */
    private BroadCastJoinTableInfo leftTable;
    /**
     * The right and big table.
     */
    private BroadCastJoinTableInfo rightTable;
    /**
     * The information of the broadcast join.
     */
    private JoinInfo joinInfo;
    /**
     * The information of the join output files.<br/>
     * <b>Note: </b>for inner, right-outer, and natural joins, the number of output files
     * should be consistent with the number of input splits in right table. For left-outer
     * and full-outer joins, there is an additional output file for the left-outer records.
     */
    private MultiOutputInfo output;

    /**
     * Default constructor for Jackson.
     */
    public BroadcastJoinInput() { }

    public BroadcastJoinInput(long queryId, BroadCastJoinTableInfo leftTable,
                              BroadCastJoinTableInfo rightTable, JoinInfo joinInfo,
                              MultiOutputInfo output)
    {
        this.queryId = queryId;
        this.leftTable = leftTable;
        this.rightTable = rightTable;
        this.joinInfo = joinInfo;
        this.output = output;
    }

    public long getQueryId()
    {
        return queryId;
    }

    public void setQueryId(long queryId)
    {
        this.queryId = queryId;
    }

    public BroadCastJoinTableInfo getLeftTable()
    {
        return leftTable;
    }

    public void setLeftTable(BroadCastJoinTableInfo leftTable)
    {
        this.leftTable = leftTable;
    }

    public BroadCastJoinTableInfo getRightTable()
    {
        return rightTable;
    }

    public void setRightTable(BroadCastJoinTableInfo rightTable)
    {
        this.rightTable = rightTable;
    }

    public JoinInfo getJoinInfo()
    {
        return joinInfo;
    }

    public void setJoinInfo(JoinInfo joinInfo)
    {
        this.joinInfo = joinInfo;
    }

    @Override
    public MultiOutputInfo getOutput()
    {
        return output;
    }

    public void setOutput(MultiOutputInfo output)
    {
        this.output = output;
    }
}