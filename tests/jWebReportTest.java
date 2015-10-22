package PSaPP.tests;
/*
Copyright (c) 2010, PMaC Laboratories, Inc.
All rights reserved.

Redistribution and use in source and binary forms, with or without modification, are
permitted provided that the following conditions are met:

 *  Redistributions of source code must retain the above copyright notice, this list of conditions
and the following disclaimer.
 *  Redistributions in binary form must reproduce the above copyright notice, this list of conditions
and the following disclaimer in the documentation and/or other materials provided with the distribution.
 *  Neither the name of PMaC Laboratories, Inc. nor the names of its contributors may be
used to endorse or promote products derived from this software without specific prior written permission.

THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND ANY EXPRESS OR IMPLIED
WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A
PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR
ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT
LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR
TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF
ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

import PSaPP.util.*;
import junit.framework.TestCase;
import java.io.*;

/**
 * This class performs black-box testing of jSimReport
 * @author bsheely
 */
public class jSimReportTest extends TestCase {

    public void testExecutable() {

        try {
            String executable = "bin/jSimReport";
            String command = executable + " --dir tests/jSimReportTestData --file icepic_standard_0064_pr41363.psinsout --save_output";
            assertNotNull(LinuxCommand.execute(command));
        } catch (Exception e) {
            fail("Exception executing jSimReport: \n" + e);
        }  
        try {
            assertTrue(TestUtils.filesEqual(new File("tests/jSimReportTestData/icepic_standard_0064_pr41363.html"),
                    new File("tests/jSimReportTestData/expected_results/icepic_standard_0064_pr41363.html")));
            assertTrue(TestUtils.filesEqual(new File("tests/jSimReportTestData/icepic_standard_0064_pr41363.txt"),
                    new File("tests/jSimReportTestData/expected_results/icepic_standard_0064_pr41363.txt")));
            /*
             * NOTE: If the images being compaired were created on the different machines, a small percentage of the pixels
             * usually differ and the comparison method simply does a pixel by pixel comparison
            assertTrue(TestUtils.imagesEqual(new File("tests/jSimReportTestData/images/icepic_standard_0064_pr41363_etasktime_barchart.png"),
                    new File("tests/jSimReportTestData/expected_results/icepic_standard_0064_pr41363_etasktime_barchart.png")));
            assertTrue(TestUtils.imagesEqual(new File("tests/jSimReportTestData/images/icepic_standard_0064_pr41363_functime_piechart.png"),
                    new File("tests/jSimReportTestData/expected_results/icepic_standard_0064_pr41363_functime_piechart.png")));
            assertTrue(TestUtils.imagesEqual(new File("tests/jSimReportTestData/images/icepic_standard_0064_pr41363_etime_piechart.png"),
                    new File("tests/jSimReportTestData/expected_results/icepic_standard_0064_pr41363_etime_piechart.png")));
            assertTrue(TestUtils.imagesEqual(new File("tests/jSimReportTestData/images/icepic_standard_0064_pr41363_hitrates_piechart.png"),
                    new File("tests/jSimReportTestData/expected_results/icepic_standard_0064_pr41363_hitrates_piechart.png")));
             */
        } catch (Exception e) {
            fail("Exception comparing files: \n" + e);
        }
    }

    protected void tearDown() {
        (new File("tests/jSimReportTestData/icepic_standard_0064_pr41363.html")).delete();
        (new File("tests/jSimReportTestData/icepic_standard_0064_pr41363.txt")).delete();
        Util.deleteDir(new File("tests/jSimReportTestData/images"));
    }
}
