/*
 *
 * Copyright (c) 2009-2012,
 *
 *  Adam Fuchs          <afuchs@cs.umd.edu>
 *  Avik Chaudhuri      <avik@cs.umd.edu>
 *  Steve Suh           <suhsteve@gmail.com>
 *
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice,
 * this list of conditions and the following disclaimer.
 *
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 * this list of conditions and the following disclaimer in the documentation
 * and/or other materials provided with the distribution.
 *
 * 3. The names of the contributors may not be used to endorse or promote
 * products derived from this software without specific prior written
 * permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE
 * LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
 * INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
 * CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
 * POSSIBILITY OF SUCH DAMAGE.
 *
 *
 */

package com.ibm.wala.dex.instructions;

import org.jf.dexlib.Code.Format.SparseSwitchDataPseudoInstruction;

public class SparseSwitchPad implements SwitchPad {

    public final int [] values;
    public final int [] offsets;
    public final int defaultOffset;
    private int [] labelsAndOffsets;

    public SparseSwitchPad(SparseSwitchDataPseudoInstruction s, int defaultOffset)
    {
        this.values = s.getKeys();
        this.offsets = s.getTargets();
        this.defaultOffset = defaultOffset;
    }

    public int [] getOffsets()
    {
        return offsets;
    }

    public int [] getValues()
    {
        return values;
    }


    public int getDefaultOffset() {
        //return Integer.MIN_VALUE;
        return defaultOffset;
    }

    public int[] getLabelsAndOffsets() {
//      return values;

        if(labelsAndOffsets != null)
            return labelsAndOffsets;
//      labelsAndOffsets = new int[offsets.length * 2 + 2];
        labelsAndOffsets = new int[offsets.length * 2];
        for(int i = 0; i < offsets.length; i++)
        {
            labelsAndOffsets[i*2] = values[i];
            labelsAndOffsets[i*2 + 1] = offsets[i];
        }
//      labelsAndOffsets[offsets.length*2] = getDefaultLabel();
//      labelsAndOffsets[offsets.length*2+1] = defaultOffset;
        return labelsAndOffsets;
    }
}
