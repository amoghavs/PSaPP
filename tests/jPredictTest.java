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
import java.io.*;
import junit.framework.TestCase;

/**
 * This class performs black-box testing of jPredict
 * @author bsheely
 */
public class jPredictTest extends TestCase {

    public void testExecutable() {
        try {
            String netsim_dir = System.getenv("PSINS_ROOT") == null ? "" : " --netsim_dir " + System.getenv("PSINS_ROOT");
            String executable = "bin/jPredict";
            String command = executable + " --funding_agency HPCMO --project ti10 --round 1" +
                    " --application icepic --dataset standard --cpu_count 64 --psins_model cont" +
                    " --direct_dir tests/jPredictTestData/ti10_round1_icepic_standard_0064" +
                    " --scratch tests/jPredictTestData --prediction_group 270606 --profile_list 40811" +
                    " --stats --use_sim_memtime --base_system 57 --shortname testStudy --no_reports" + netsim_dir;
            assertNotNull(LinuxCommand.execute(command));
        } catch (Exception e) {
            fail("Exception executing jSimReport: \n" + e);
        }
        try {
            assertTrue(TestUtils.filesEqual(new File("tests/jPredictTestData/270606/icepic_standard_0064/br143_pr40811_mp271303_sysid77.con"),
                    new File("tests/jPredictTestData/expected_results/br143_pr40811_mp271303_sysid77.con")));
            assertTrue(TestUtils.filesEqual(new File("tests/jPredictTestData/270606/icepic_standard_0064/br143_pr40811_np270893.net"),
                    new File("tests/jPredictTestData/expected_results/br143_pr40811_np270893.net")));
            assertTrue(TestUtils.filesEqual(new File("tests/jPredictTestData/270606/icepic_standard_0064/br143_pr40811_np270893_psins.config"),
                    new File("tests/jPredictTestData/expected_results/br143_pr40811_np270893_psins.config")));
            assertTrue(TestUtils.filesEqual(new File("tests/jPredictTestData/270606/icepic_standard_0064/br143_pr40811_np270893_psins.output"),
                    new File("tests/jPredictTestData/expected_results/br143_pr40811_np270893_psins.output")));
            assertTrue(TestUtils.filesEqual(new File("tests/jPredictTestData/270606/icepic_standard_0064/br57_pr35244_mp30293_sysid22.bas"),
                    new File("tests/jPredictTestData/expected_results/br57_pr35244_mp30293_sysid22.bas")));
            assertTrue(TestUtils.filesEqual(new File("tests/jPredictTestData/270606/icepic_standard_0064/icepic_standard_0064_pr40811.psinsout"),
                    new File("tests/jPredictTestData/expected_results/icepic_standard_0064_pr40811.psinsout")));
            assertTrue(TestUtils.filesEqual(new File("tests/jPredictTestData/270606/icepic_standard_0064/stats/0000_br143_np270893.comm"),
                    new File("tests/jPredictTestData/expected_results/0000_br143_np270893.comm")));
            assertTrue(TestUtils.filesEqual(new File("tests/jPredictTestData/270606/icepic_standard_0064/stats/sysid77_0000_br143_mp271303.bins"),
                    new File("tests/jPredictTestData/expected_results/sysid77_0000_br143_mp271303.bins")));
            assertTrue(TestUtils.filesEqual(new File("tests/jPredictTestData/270606/icepic_standard_0064/stats/sysid77_0000_br143_mp271303.func"),
                    new File("tests/jPredictTestData/expected_results/sysid77_0000_br143_mp271303.func")));
            assertTrue(TestUtils.filesEqual(new File("tests/jPredictTestData/270606/icepic_standard_0064/stats/sysid77_0000_br143_mp271303.task"),
                    new File("tests/jPredictTestData/expected_results/sysid77_0000_br143_mp271303.task")));
        } catch (Exception e) {
            fail("Exception comparing files: \n" + e);
        }
    }
    
    protected void tearDown() {
        Util.deleteDir(new File("tests/jPredictTestData/270606"));
    }
}
