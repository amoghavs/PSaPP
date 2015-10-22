package PSaPP.dbase;
/*
Copyright (c) 2010, The Regents of the University of California
All rights reserved.

Redistribution and use in source and binary forms, with or without modification, are
permitted provided that the following conditions are met:

 *  Redistributions of source code must retain the above copyright notice, this list of conditions
and the following disclaimer.
 *  Redistributions in binary form must reproduce the above copyright notice, this list of conditions
and the following disclaimer in the documentation and/or other materials provided with the distribution.
 *  Neither the name of the Regents of the University of California nor the names of its contributors may be
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
import java.util.*;
import java.sql.*;
import java.lang.*;

public final class Postgres extends Database {

    Connection postgresDB;
    String dbUser;
    String dbPass;
    boolean allowDuplicates = false;

    int psinsTraceTypeDbid() {
        return 141133;
    }

    int mpidTraceTypeDbid() {
        return 141132;
    }

    int paramExppenDropDbid() {
        return 136073;
    }

    int paramStretchedExpDbid() {
        return 136075;
    }

    int paramStretchedHitDbid() {
        return 165064;
    }

    int paramStretchedEMultDbid() {
        return 174177;
    }

    int paramStretchedPenDbid() {
        return 168010;
    }

    int paramCyclesDropDbid() {
        return 170106;
    }

    int methStretchedExpDbid() {
        return 141378;
    }

    int methExppenDropDbid() {
        return 141458;
    }

    String formUpdateQuery(Object[] values, String[] columns, String tableName, Object[] setValues, String[] setColumns) {
        if ((tableName == null) || (values == null) || (setValues == null)) {
            return null;
        }
        int minColCount = values.length;
        if (columns != null) {
            if (values.length < columns.length) {
                return null;
            }
            minColCount = columns.length;
        }
        int minValColCount = setValues.length;
        if (setColumns != null) {
            if (setValues.length < setColumns.length) {
                return null;
            }
            minValColCount = setColumns.length;
        }

        String queryStr = "update " + tableName + " set ";
        for (int i = 0; i < minValColCount; i++) {
            queryStr += (setColumns[i] + " = ");
            if (setValues[i] instanceof String) {
                queryStr += ("'" + setValues[i] + "'");
            } else {
                queryStr += setValues[i];
            }
            if (i != (minValColCount - 1)) {
                queryStr += ",";
            }
        }

        queryStr += " where ";
        for (int i = 0; i < minColCount; i++) {
            queryStr += (columns[i] + " = ");
            if (values[i] instanceof String) {
                queryStr += ("'" + values[i] + "'");
            } else {
                queryStr += values[i];
            }
            if (i != (minColCount - 1)) {
                queryStr += " and ";
            }
        }
        queryStr += " returning dbid";
        Logger.debug(queryStr);

        return queryStr;
    }

    String formInsertQuery(Object[] values, String[] columns, String tableName) {
        if ((tableName == null) || (values == null)) {
            return null;
        }
        int minColCount = values.length;
        if (columns != null) {
            if (values.length < columns.length) {
                return null;
            }
            minColCount = columns.length;
        }

        String queryStr = "INSERT INTO " + tableName;
        if (columns != null) {
            queryStr += " ( ";
            for (int i = 0; i < columns.length; i++) {
                queryStr += columns[i];
                if (i != (columns.length - 1)) {
                    queryStr += " , ";
                }
            }
            queryStr += " ) ";
        }
        queryStr += " VALUES ( ";
        for (int i = 0; i < minColCount; i++) {
            if (values[i] instanceof String) {
                queryStr += "'" + values[i] + "'";
            } else {
                queryStr += values[i];
            }
            if (i != (minColCount - 1)) {
                queryStr += " , ";
            }
        }
        queryStr += " )";
        queryStr += " returning dbid";
        Logger.debug(queryStr);

        return queryStr;
    }

    int updateRow(Object value, String column, String tableName, Object setValue, String setColumn) {
        return updateRow(Util.makeArray(value), Util.makeArray(column),
                tableName,
                Util.makeArray(setValue), Util.makeArray(setColumn));
    }

    int updateRow(Object value, String column, String tableName, Object[] setValues, String setColumns[]) {
        return updateRow(Util.makeArray(value), Util.makeArray(column),
                tableName, setValues, setColumns);
    }

    int updateRow(Object[] values, String[] columns, String tableName, Object[] setValues, String[] setColumns) {
        String queryStr = formUpdateQuery(values, columns, tableName, setValues, setColumns);
        if (queryStr == null) {
            Logger.warn("Update query string is null");
            return InvalidDBID;
        }
        int retValue = InvalidDBID;
        try {
            Statement st = postgresDB.createStatement();
            ResultSet rs = st.executeQuery(queryStr);
            while (rs.next()) {
                Object value = rs.getObject("dbid");
                assert (value instanceof Integer);
                retValue = ((Integer) value).intValue();
            }
        } catch (SQLException e) {
            Logger.warn("Update to table \"" + tableName + "\" failed\n" + e);
            return InvalidDBID;
        }
        return retValue;
    }

    int insertRow(Object[] values, String[] columns, String tableName) {
        String queryStr = formInsertQuery(values, columns, tableName);
        if (queryStr == null) {
            Logger.warn("Query string is null for insertion");
            return InvalidDBID;
        }
        int retValue = InvalidDBID;
        try {
            Statement st = postgresDB.createStatement();
            ResultSet rs = st.executeQuery(queryStr);
            while (rs.next()) {
                Object value = rs.getObject("dbid");
                assert (value instanceof Integer);
                retValue = ((Integer) value).intValue();
            }
        } catch (SQLException e) {
            Logger.warn("Insert to table \"" + tableName + "\" failed\n" + e);
            return InvalidDBID;
        }
        return retValue;
    }

    public int addPredictionGroup(String[] fields) {
        Object[] tokens = Util.stringsToRecord(fields, predictionGroupsSignature);
        if (tokens == null) {
            Logger.warn("One or more entry in prediction group is invalid " + Util.listToString(fields));
            return InvalidDBID;
        }
        String[] columns = {"name", "comments"};
        return insertRow(tokens, columns, "prediction_groups");
    }

    public int addMachineProfile(String[] fields) {
        Object[] tokens = Util.stringsToRecord(fields, machineProfilesSignature);
        if (tokens == null) {
            Logger.warn("One or more entry in machine benchmark data is invalid " + Util.listToString(fields));
            return InvalidDBID;
        }
        boolean status = existsBaseResource(((Integer) tokens[0]).intValue())
                && (getMemoryProfile(((Integer) tokens[1]).intValue()) != null)
                && (getNetworkProfile(((Integer) tokens[2]).intValue()) != null);
        String[] columns = {"base_resource", "memory_benchmark_data", "network_benchmark_data",
            "cache_structure", "nickname"};
        return insertRow(tokens, columns, "machine_profiles");
    }

    public int addNetworkBenchmarkData(String[] fields) {
        Object[] tokens = Util.stringsToRecord(fields, networkProfilesSignature);
        if (tokens == null) {
            Logger.warn("One or more entry in network benchmark data is invalid " + Util.listToString(fields));
            return InvalidDBID;
        }
        String[] columns = {"off_node_network_bandwidth", "off_node_network_latency",
            "on_node_network_bandwidth", "on_node_network_latency",
            "processors_per_node", "network_bus_count", "input_link_count",
            "output_link_count"};
        return insertRow(tokens, columns, "network_benchmark_data");
    }

    public int addBaseResource(String[] fields) {
        Object[] tokens = Util.stringsToRecord(fields, baseResourcesSignature);
        if (tokens == null) {
            Logger.warn("One or more entry in base resource is invalid " + Util.listToString(fields));
            return InvalidDBID;
        }
        Object sameNameRes = existsRow(tokens[7], "nickname", "base_resources", "dbid");
        if (sameNameRes != null) {
            Logger.warn(tokens[7] + " is used for another resource " + sameNameRes);
            return InvalidDBID;
        }
        String[] columns = {"center", "vendor", "processor_type", "processor_count",
            "processors_per_node", "processor_speed_hz", "flop_per_cycle",
            "nickname"};
        return insertRow(tokens, columns, "base_resources");
    }

    public int addMemoryBenchmarkData(String[] fields) {
        Integer levelCount = Util.toInteger(fields[2]);
        boolean status = Util.isValidBWMethod(fields[1]) && ((levelCount != null)
                && ((levelCount.intValue() > 0) && (levelCount.intValue() <= 3)));
        if (!status) {
            Logger.warn("One or more entry in memory profile is invalid " + Util.listToString(fields));
            return InvalidDBID;
        }
        String restOfSig = Util.getMemoryProfileSignature((String) fields[1], levelCount);
        String signature = memoryProfilesSignature + restOfSig;
        Object[] tokens = Util.stringsToRecord(fields, signature);
        if (tokens == null) {
            Logger.warn("One or more entry in memory benchmark data is invalid " + Util.listToString(fields));
            return InvalidDBID;
        }

        int idx = 0;
        int colCount = 0;
        String[] columns = null;
        Object[] values = null;
        int paramNamesId = InvalidDBID;
        if (fields[1].equals("BWstretchedExp") || fields[1].equals("ti09")) {
            paramNamesId = paramStretchedExpDbid();
            colCount = levelCount.intValue() * 3 + 3;
            columns = new String[colCount];
            values = new Object[colCount];
            for (int j = 0; j < 3; j++) {
                columns[idx] = "parameter" + (j * 3 + 1);
                values[idx] = tokens[idx + 3];
                idx++;
                for (int i = 1; i < levelCount.intValue(); i++) {
                    columns[idx] = "parameter" + (j * 3 + 3 - (levelCount.intValue() - i) + 1);
                    values[idx] = tokens[idx + 3];
                    idx++;
                }
            }
        } else if (fields[1].equals("BWstretchedHit")) {
            paramNamesId = paramStretchedHitDbid();
            colCount = (levelCount.intValue() * 3) + (levelCount.intValue() - 1) + 2;
            columns = new String[colCount];
            values = new Object[colCount];
            for (int j = 0; j < 3; j++) {
                columns[idx] = "parameter" + (j * 3 + 1);
                values[idx] = tokens[idx + 3];
                idx++;
                for (int i = 1; i < levelCount.intValue(); i++) {
                    columns[idx] = "parameter" + (j * 3 + 3 - (levelCount.intValue() - i) + 1);
                    values[idx] = tokens[idx + 3];
                    idx++;
                }
            }
            for (int i = 1; i < levelCount.intValue(); i++) {
                columns[idx] = "parameter" + ((3 * 3) + i);
                values[idx] = tokens[idx + 3];
                idx++;
            }
        } else if (fields[1].equals("BWstretchedEMult")) {
            paramNamesId = paramStretchedEMultDbid();
            colCount = (levelCount.intValue() * 2) + 1 + 2;
            columns = new String[colCount];
            values = new Object[colCount];
            columns[idx] = "parameter1";
            values[idx] = tokens[idx + 3];
            idx++;
            for (int j = 0; j < 2; j++) {
                columns[idx] = "parameter" + (j * 3 + 2);
                values[idx] = tokens[idx + 3];
                idx++;
                for (int i = 1; i < levelCount.intValue(); i++) {
                    columns[idx] = "parameter" + (j * 3 + 3 - (levelCount.intValue() - i) + 2);
                    values[idx] = tokens[idx + 3];
                    idx++;
                }
            }
        } else if (fields[1].equals("BWstretchedPen")) {
            paramNamesId = paramStretchedPenDbid();
            colCount = (levelCount.intValue() * 3) + 1 + (levelCount.intValue() - 1) + 2;
            columns = new String[colCount];
            values = new Object[colCount];
            int addOne = 0;
            for (int j = 0; j < 3; j++) {
                columns[idx] = "parameter" + (j * 3 + 1 + addOne);
                values[idx] = tokens[idx + 3];
                idx++;
                for (int i = 1; i < levelCount.intValue(); i++) {
                    columns[idx] = "parameter" + (j * 3 + 3 - (levelCount.intValue() - i) + 1 + addOne);
                    values[idx] = tokens[idx + 3];
                    idx++;
                }
                if (addOne == 0) {
                    addOne = 1;
                    columns[idx] = "parameter4";
                    values[idx] = tokens[idx + 3];
                    idx++;
                }
            }
            for (int i = 1; i < levelCount.intValue(); i++) {
                columns[idx] = "parameter" + ((3 * 3) + addOne + i);
                values[idx] = tokens[idx + 3];
                idx++;
            }
        } else if (fields[1].equals("BWcyclesDrop")) {
            paramNamesId = paramCyclesDropDbid();
            colCount = levelCount.intValue() + 1 + 2;
            columns = new String[colCount];
            values = new Object[colCount];
            columns[idx] = "parameter1";
            values[idx] = tokens[idx + 3];
            idx++;
            columns[idx] = "parameter2";
            values[idx] = tokens[idx + 3];
            idx++;
            for (int i = 1; i < levelCount.intValue(); i++) {
                columns[idx] = "parameter" + (4 - (levelCount.intValue() - i) + 1);
                values[idx] = tokens[idx + 3];
                idx++;
            }
        } else if (fields[1].equals("BWexppenDrop")) {
            paramNamesId = paramExppenDropDbid();
            colCount = (levelCount.intValue() + 1) * 2 + 2 * 2 + 2;
            columns = new String[colCount];
            values = new Object[colCount];
            for (int j = 0; j < 2; j++) {
                for (int i = 0; i < levelCount.intValue(); i++) {
                    columns[idx] = "parameter" + (j * 4 + i + 1);
                    values[idx] = tokens[idx + 3];
                    idx++;
                }
                columns[idx] = "parameter" + (j * 4 + 4);
                values[idx] = tokens[idx + 3];
                idx++;
            }
            for (int j = 0; j < 2; j++) {
                for (int i = 0; i < 2; i++) {
                    columns[idx] = "parameter" + (9 + j * 2 + i);
                    values[idx] = tokens[idx + 3];
                    idx++;
                }
            }
        }

        columns[idx] = "label";
        values[idx] = tokens[idx + 3];
        idx++;

        columns[idx] = "memory_parameter_names";
        values[idx] = new Integer(paramNamesId);
        idx++;

        columns[idx] = "zone_weight";
        values[idx] = tokens[0];
        idx++;

        assert (idx == colCount);

        return insertRow(values, columns, "memory_benchmark_data");
    }

    public int addTestCase(String[] fields) {
        Object[] tokens = Util.stringsToRecord(fields, testCasesSignature);
        if (tokens == null) {
            Logger.warn("One or more entry in base resource is invalid " + Util.listToString(fields));
            return InvalidDBID;
        }
        boolean status = ConfigSettings.isValid("AGENCIES", (String) tokens[0])
                && ConfigSettings.isValid("PROJECTS", (String) tokens[1])
                && Util.isValidRound((Integer) tokens[2])
                && ConfigSettings.isValid("APPLICATIONS", (String) tokens[3])
                && ConfigSettings.isValid("SIZES", (String) tokens[4])
                && Util.isValidTaskCount((Integer) tokens[5]);
        if (status) {
            TestCase tc = new TestCase((String) tokens[3], (String) tokens[4], (Integer) tokens[5],
                    (String) tokens[0], (String) tokens[1], (Integer) tokens[2]);
            if (existsTestCase(tc)) {
                Logger.warn("The test case already exists in database for " + tc);
                return InvalidDBID;
            }
        } else {
            Logger.warn("One or more entry in test case is invalid");
            return InvalidDBID;
        }

        String[] columns = {"funding_agency", "project", "round", "application", "dataset", "cpu_count", "network_trace_type"};
        Object[] newTokens = Util.concatArray(tokens, new Integer(psinsTraceTypeDbid()));
        return insertRow(newTokens, columns, "test_case_data");
    }

    public int addActualRuntime(String[] fields) {
        Object[] tokens = Util.stringsToRecord(fields, actualRuntimesSignature);
        if (tokens == null) {
            Logger.warn("One or more entry in actual runtime is invalid " + Util.listToString(fields));
            return InvalidDBID;
        }
        boolean status = existsBaseResource(((Integer) tokens[1]).intValue())
                && (existsRow((Integer) tokens[0], "dbid", "test_case_data", "dbid") != null);
        if (!status) {
            Logger.warn("One or more entry in actual runtime is invalid");
            return InvalidDBID;
        }

        String[] selCols = {"test_case_data", "base_resource"};
        Object[] selValues = new Object[2];
        selValues[0] = tokens[0];
        selValues[1] = tokens[1];
        Object value = existsRow(selValues, selCols, "actual_runtimes", "dbid");
        if (value == null) {
            String[] columns = {"test_case_data", "base_resource", "runtime"};
            return insertRow(tokens, columns, "actual_runtimes");
        }
        assert (value instanceof Integer);
        if (updateRow(value, "dbid", "actual_runtimes", tokens[2], "runtime") == InvalidDBID) {
            Logger.warn("Update failed for actual_runtimes");
            return InvalidDBID;
        }
        return ((Integer) value).intValue();
    }

    public int addDefaultProfile(String[] fields) {
        Object[] tokens = Util.stringsToRecord(fields, defaultProfilesSignature);
        if (tokens == null) {
            Logger.warn("One or more entry in default profile data is invalid " + Util.listToString(fields));
            return InvalidDBID;
        }
        boolean status = existsBaseResource(((Integer) tokens[0]).intValue())
                && existsProfile(((Integer) tokens[1]).intValue());
        if (!status) {
            Logger.warn("One or more entry in default profiles is invalid");
            return InvalidDBID;
        }
        Object value = existsRow(tokens[0], "base_resource", "default_profiles", "dbid");
        if (value == null) {
            String[] columns = {"base_resource", "machine_profile"};
            return insertRow(tokens, columns, "default_profiles");
        }
        assert (value instanceof Integer);
        if (updateRow(value, "dbid", "default_profiles", tokens[1], "machine_profile") == InvalidDBID) {
            Logger.warn("Update failed for default_profiles");
            return InvalidDBID;
        }
        return ((Integer) value).intValue();
    }

    public int addZoneWeights(String[] fields) {
        Object tokens[] = Util.stringsToRecord(fields, zoneWeightsSignature);
        if (tokens == null) {
            Logger.warn("One or more entry in zone weights is invalid " + Util.listToString(fields));
            return InvalidDBID;
        }
        String columns[] = {"zone0_weight", "zone1_weight", "zone2_weight", "zone3_weight", "zone4_weight",
            "zone5_weight", "zone6_weight", "zone7_weight", "zone8_weight", "zone9_weight", "zone10_weight",
            "zone11_weight", "zone12_weight", "zone13_weight", "zone14_weight", "zone15_weight", "zone16_weight",
            "zone17_weight", "zone18_weight", "zone19_weight", "zone20_weight", "comment"};
        return insertRow(tokens, columns, "zone_weights");
    }

    public int addApplicationZoneWeight(String[] fields) {
        Object tokens[] = Util.stringsToRecord(fields, applicationZoneWeightSignature);
        if (tokens == null) {
            Logger.warn("One or more entry in application zone weight is invalid " + Util.listToString(fields));
            return InvalidDBID;
        }
        String columns[] = {"cache_structure", "test_case_data", "zone_weight"};
        return insertRow(tokens, columns, "application_zone_weight");
    }

    public int addCacheStructures(int levelCount, String[] fields) {
        Object tokens[] = Util.stringsToRecord(fields, getCacheStructuresSignature(levelCount, true));
        if (tokens == null) {
            Logger.warn("One or more entry in cache structures is invalid " + Util.listToString(fields));
            return InvalidDBID;
        }
        if (levelCount == 3) {
            String columns[] = {"l1_size", "l2_size", "l3_size", "l1_associativity", "l2_associativity",
                "l3_associativity", "l1_linesize", "l2_linesize", "l3_linesize", "l1_replacement_policy",
                "l2_replacement_policy", "l3_replacement_policy", "comments"};
            return insertRow(tokens, columns, "cache_structures");
        } else if (levelCount == 2) {
            String columns[] = {"l1_size", "l2_size", "l1_associativity", "l2_associativity", "l1_linesize",
                "l2_linesize", "l1_replacement_policy", "l2_replacement_policy", "comments"};
            return insertRow(tokens, columns, "cache_structures");
        } else if (levelCount == 1) {
            String columns[] = {"l1_size", "l1_associativity", "l1_linesize", "l1_replacement_policy", "comments"};
            return insertRow(tokens, columns, "cache_structures");
        }
        return InvalidDBID;
    }

    public Postgres() {
        dbUser = LinuxCommand.username();
        postgresDB = null;
        dbPass = null;
    }

    public int getSimMemoryTimeGroup() {
        return 59134;
    }

    public int getSimReportGroup() {
        return 300420;
    }

    public boolean initialize() {
        try {
            String db = ConfigSettings.getSetting("DATABASE");
            Logger.inform("Initializing " + db);
            Class.forName("org.postgresql.Driver");
            postgresDB = DriverManager.getConnection(db, dbUser, dbPass);
            Logger.inform("Initialized " + db);
        } catch (Exception e) {
            Logger.error(e, "Can not load the database driver\n");
            return false;
        }
        return true;
    }

    public boolean commit() {
        Logger.inform("Closing database connection");
        if (postgresDB != null) {
            try {
                postgresDB.close();
            } catch (Exception e) {
                Logger.warn("Can not commit to the database");
                return false;
            }
        }
        return true;
    }

    String formSelectQuery(Object[] values, String[] columns, String tableName) {
        if (tableName == null) {
            return null;
        }
        if ((values == null) || (columns == null)) {
            return ("select * from " + tableName);
        }
        if (values.length != columns.length) {
            return null;
        }
        String queryStr = "select * from " + tableName + " where ";
        for (int i = 0; i < values.length; i++) {
            queryStr += (columns[i] + " = ");
            if (values[i] instanceof String) {
                queryStr += ("'" + values[i] + "'");
            } else {
                queryStr += values[i];
            }
            if (i != (values.length - 1)) {
                queryStr += " and ";
            }
        }
        return queryStr;
    }

    Object existsRow(int value, String cName, String tableName, String selectCol) {
        return existsRow(new Integer(value), cName, tableName, selectCol);
    }

    Object[] existsRow(int value, String cName, String tableName, String[] selectCols) {
        return existsRow(new Integer(value), cName, tableName, selectCols);
    }

    Object existsRow(Object value, String cName, String tableName, String selectCol) {
        Object[] valueArray = new Object[1];
        String[] columnNames = new String[1];
        valueArray[0] = value;
        columnNames[0] = cName;

        return existsRow(valueArray, columnNames, tableName, selectCol);
    }

    Object[] existsRow(Object value, String cName, String tableName, String[] selectCols) {
        Object[] valueArray = new Object[1];
        String[] columnNames = new String[1];
        valueArray[0] = value;
        columnNames[0] = cName;
        return existsRow(valueArray, columnNames, tableName, selectCols);
    }

    Object existsRow(Object[] values, String[] columns, String tableName, String selectCol) {
        String[] valCols = new String[1];
        valCols[0] = selectCol;
        Object[] retValues = existsRow(values, columns, tableName, valCols);
        if (retValues == null) {
            return null;
        }
        return retValues[0];
    }

    Object[] existsRow(Object[] values, String[] columns, String tableName, String[] selectCols) {
        String queryStr = formSelectQuery(values, columns, tableName);

        if (queryStr == null) {
            Logger.warn("Select query string is null");
            return null;
        }
        int rowCount = 0;
        Object[] retValue = null;
        try {
            Statement st = postgresDB.createStatement();
            ResultSet rs = st.executeQuery(queryStr);
            ResultSetMetaData meta = rs.getMetaData();
            if (selectCols != null) {
                retValue = new Object[selectCols.length];
            } else {
                retValue = new Object[meta.getColumnCount()];
            }
            while (rs.next()) {
                if (selectCols != null) {
                    for (int i = 0; i < selectCols.length; i++) {
                        retValue[i] = rs.getObject(selectCols[i]);
                    }
                } else {
                    for (int i = 0; i < meta.getColumnCount(); i++) {
                        retValue[i] = rs.getObject(i + 1);
                    }
                }
                rowCount++;
            }
            rs.close();
            st.close();

        } catch (Exception e) {
            Logger.warn("Can not list the table \"" + tableName + "\"\n" + e);
            return null;
        }

        if (!allowDuplicates) {
            assert ((rowCount == 0) || (rowCount == 1));
        } else if (rowCount > 1) {
            Logger.inform(queryStr + " matched " + String.valueOf(rowCount) + " rows");
        }
        if (rowCount == 0) {
            return null;
        }
        return retValue;
    }

    public int getSysidFromBaseResource(int baseResource){
        assert(existsBaseResource(baseResource));
        int machProf = getDefaultPIdx(baseResource);
        assert(existsProfile(machProf));
        return getCacheSysId(machProf);
    }

    public boolean existsTestCase(int testCaseId) {
        return (existsRow(testCaseId, "dbid", "test_case_data", "dbid") != null);
    }

    public boolean existsPredGroup(int group) {
        return (existsRow(group, "dbid", "prediction_groups", "dbid") != null);
    }

    public boolean existsBaseResource(int machIdx) {
        return (existsRow(machIdx, "dbid", "base_resources", "dbid") != null);
    }

    public boolean existsProfile(int profIdx) {
        return (existsRow(profIdx, "dbid", "machine_profiles", "dbid") != null);
    }

//LAURA added
    public int existsCacheStructureL1Line(int cacheStructure){
        Object cacheS= existsRow(cacheStructure, "dbid", "cache_structures", "l1_linesize") ;
        if (cacheS == null) {
            //LAURA problem
            return -999;
        }
        assert (cacheS instanceof Integer);
        return ((Integer) cacheS).intValue();
    }
    public int existsCacheStructureL2Line(int cacheStructure){
        Object cacheS= existsRow(cacheStructure, "dbid", "cache_structures", "l2_linesize") ;
        if (cacheS == null) {
            //LAURA problem
            return -999;
        }
        assert (cacheS instanceof Integer);
        return ((Integer) cacheS).intValue();
    }
    public int existsCacheStructureL3Line(int cacheStructure){
        Object cacheS= existsRow(cacheStructure, "dbid", "cache_structures", "l3_linesize") ;
        if (cacheS == null) {
            //LAURA problem
            return -999;
        }
        assert (cacheS instanceof Integer);
        return ((Integer) cacheS).intValue();
    }
//LAURA

    public synchronized int getTrainedMachineProfile(int zoneWeightIdx, int baseResource){
        Logger.inform("Looking up trained profile for zoneweight " + zoneWeightIdx + " and baseResource " + baseResource);

        String queryStr = "select machine_profiles.* from machine_profiles, memory_benchmark_data, zone_weights " + 
            "where memory_benchmark_data.dbid = machine_profiles.memory_benchmark_data and " + 
            "memory_benchmark_data.zone_weight = zone_weights.dbid and " + 
            "zone_weights.dbid = " + zoneWeightIdx + " and " +
            "machine_profiles.base_resource = " + baseResource;
        Logger.inform("zone_weights query: " + queryStr);

        int retValue = InvalidDBID;
        int rowCount = 0;
        try {
            Statement st = postgresDB.createStatement();
            ResultSet rs = st.executeQuery(queryStr);
            while (rs.next()) {
                retValue = rs.getInt("dbid");
                rowCount++;
            }
            rs.close();
            st.close();
        } catch (Exception e) {
            Logger.error(e, "Can not list the test case data for dbid\n" + e);
            return InvalidDBID;
        }
        return retValue;
    }

    public boolean existsMemoryBenchmarkData(int zoneWeightIdx, int baseResource) {
        allowDuplicates=true; /* LAURA */
        Object memoryBenchmarkData = existsRow(zoneWeightIdx, "zone_weight", "memory_benchmark_data", "dbid");
        allowDuplicates=false; /* LAURA */
        if (memoryBenchmarkData == null) {
            return false;
        }
        assert (memoryBenchmarkData instanceof Integer);
        Object values[] = {new Integer(baseResource), memoryBenchmarkData};
        String columns[] = {"base_resource", "memory_benchmark_data"};
        return existsRow(values, columns, "machine_profiles", "dbid") != null;
    }

    public boolean existsApplicationZoneWeight(int cacheSysId, int testCaseDataIdx, int zoneWeightIdx) {
        String columns[] = {"cache_structure", "test_case_data", "zone_weight"};
        Object values[] = {new Integer(cacheSysId), new Integer(testCaseDataIdx), new Integer(zoneWeightIdx)};
        allowDuplicates=true;
        boolean hasAZW = (existsRow(values, columns, "application_zone_weight", "dbid") != null);
        allowDuplicates=false;

        return hasAZW;
    }

    public int existsZoneWeights(double[] weights, double epsilon) {
        String columns[] = {"dbid", "zone0_weight", "zone1_weight", "zone2_weight", "zone3_weight", "zone4_weight",
            "zone5_weight", "zone6_weight", "zone7_weight", "zone8_weight", "zone9_weight", "zone10_weight",
            "zone11_weight", "zone12_weight", "zone13_weight", "zone14_weight", "zone15_weight", "zone16_weight",
            "zone17_weight", "zone18_weight", "zone19_weight", "zone20_weight", "comment"};
        String rows[] = getAllRowsFormatted("zone_weights", columns, "dbid");
        for (int i = 0; i < rows.length; i++) {
            StringTokenizer tokenizer = new StringTokenizer(rows[i], "|");
            int index = 1;
            int dbid = InvalidDBID;
            int totalMatches = 0;
            while (tokenizer.hasMoreTokens()) {
                String token = tokenizer.nextToken().trim();
                if (index == 1) {
                    dbid = Integer.parseInt(token);
                } else if (index < columns.length) {
                    if (Math.abs(Double.parseDouble(token) - weights[index - 2]) <= epsilon) {
                        ++totalMatches;
                    }
                }
                ++index;
            }
            if (totalMatches == 21) {
                return dbid;
            }
        }
        return InvalidDBID;
    }

    public int existsCacheStructures(int cacheLevels, String[] fields) {
        Object dbid = null;
        String[] withoutComment = new String[fields.length - 1];
        System.arraycopy(fields, 0, withoutComment, 0, fields.length - 1);
        Object[] values = Util.stringsToRecord(withoutComment, getCacheStructuresSignature(cacheLevels, false));
        if (values == null) {
            Logger.warn("One or more entry in cache structures is invalid " + Util.listToString(fields));
            return InvalidDBID;
        }
        allowDuplicates = true;
        if (cacheLevels == 3) {
            String[] columns = {"l1_size", "l2_size", "l3_size", "l1_associativity",
                "l2_associativity", "l3_associativity", "l1_linesize", "l2_linesize", "l3_linesize",
                "l1_replacement_policy", "l2_replacement_policy", "l3_replacement_policy"};
            dbid = existsRow(values, columns, "cache_structures", "dbid");
        } else if (cacheLevels == 2) {
            String[] columns = {"l1_size", "l2_size", "l1_associativity", "l2_associativity",
                "l1_linesize", "l2_linesize", "l1_replacement_policy", "l2_replacement_policy"};
            dbid = existsRow(values, columns, "cache_structures", "dbid");
        } else if (cacheLevels == 1) {
            String[] columns = {"l1_size", "l1_associativity", "l1_linesize", "l1_replacement_policy"};
            dbid = existsRow(values, columns, "cache_structures", "dbid");
        }
        allowDuplicates = false;
        if (dbid == null) {
            return InvalidDBID;
        }
        assert (dbid instanceof Integer);
        return ((Integer) dbid).intValue();
    }

    boolean listTable(String[] fsOfInterest, String tableName,
            boolean order, String inpQ) {
        String queryStr = null;
        if (inpQ != null) {
            queryStr = inpQ;
        } else {
            queryStr = "select * from " + tableName;
            if (order) {
                queryStr += " order by dbid";
            }
        }
        try {
            Statement st = postgresDB.createStatement();
            ResultSet rs = st.executeQuery(queryStr);
            while (rs.next()) {
                String tmp = "";
                for (int i = 0; i < fsOfInterest.length; i++) {
                    Object val = null;
                    if ((fsOfInterest[i] != null) && (fsOfInterest[i].length() != 0)) {
                        val = rs.getObject(fsOfInterest[i]);
                    }
                    if (val != null) {
                        tmp += val;
                    }
                    tmp += " | ";
                }
                Logger.inform(tmp);
            }
            rs.close();
            st.close();
        } catch (Exception e) {
            Logger.warn("Can not list the table \"" + tableName + "\" completely\n" + e);
            return false;
        }
        return true;
    }

    public boolean listTestCase(String header, int tokenCnt) {
        String[] fsOfInterest = {"dbid", "funding_agency", "project", "round", "application",
            "dataset", "cpu_count"};
        Logger.inform(header);
        Logger.inform(Util.listToString(fsOfInterest));
        return listTable(fsOfInterest, "test_case_data", true, null);
    }

    public boolean listBaseResource(String header, int tokenCnt) {
        String[] fsOfInterest = {"dbid", "center", "vendor", "processor_type",
            "processor_count", "processors_per_node", "processor_speed_hz",
            "flop_per_cycle", "nickname"};
        Logger.inform(header);
        Logger.inform(Util.listToString(fsOfInterest));
        return listTable(fsOfInterest, "base_resources", true, null);
    }

    public boolean listPredictionGroup(String header, int tokenCnt) {
        String[] fsOfInterest = {"dbid", "name", "comments"};
        Logger.inform(header);
        Logger.inform(Util.listToString(fsOfInterest));
        return listTable(fsOfInterest, "prediction_groups", true, null);
    }

    public boolean listPredictionResult(String header, int tokenCnt, int group) {
        String[] fsOfInterest = {"project", "application", "dataset", "cpu_count", "machine_profile", "base_resource",
            "memory_time", "ratio", "predicted_runtime", "actual_runtime", "relative_error",
            "shortname"};
        Logger.inform(header);
        Logger.inform("PredictionGroup : " + group);
        Logger.inform(Util.listToString(fsOfInterest));
        return listTable(fsOfInterest,
                "basic_prediction(" + group + ") order by shortname,application,dataset,cpu_count",
                false, null);
    }

    public boolean listActualRuntime(String header, int tokenCnt) {
        String[] fsOfInterest = {"test_case_data", "base_resource", "runtime"};
        Logger.inform(header);
        Logger.inform(Util.listToString(fsOfInterest));
        return listTable(fsOfInterest, "actual_runtimes", false, null);
    }

    public boolean listDefaultProfile(String header, int tokenCnt) {
        String[] fsOfInterest = {"base_resource", "machine_profile"};
        Logger.inform(header);
        Logger.inform(Util.listToString(fsOfInterest));
        return listTable(fsOfInterest, "default_profiles", false, null);
    }

    public boolean listMachineProfile(String header, int tokenCnt) {
        String[] fsOfInterest = {"dbid", "base_resource",
            "memory_benchmark_data", "network_benchmark_data",
            "cache_structure", "nickname"};
        Logger.inform(header);
        Logger.inform(Util.listToString(fsOfInterest));
        return listTable(fsOfInterest, "machine_profiles", false, null);
    }

    public boolean listNetworkBenchmarkData(String header, int tokenCnt) {
        String[] fsOfInterest = {"dbid", "off_node_network_bandwidth", "off_node_network_latency",
            "on_node_network_bandwidth", "on_node_network_latency",
            "processors_per_node", "network_bus_count",
            "input_link_count", "output_link_count"};
        Logger.inform(header);
        Logger.inform(Util.listToString(fsOfInterest));
        return listTable(fsOfInterest, "network_benchmark_data", true, null);
    }

    public boolean listTraceStatus(String header, int tokenCnt) {
        String[] fsOfInterest = {"dbid",
            "executable_date_rcvd", "executable_src_machine", "executable_user", "executable_filename", "",
            "network_trace_date_rcvd", "network_trace_src_machine", "network_trace_user", "network_trace_location",
            "network_trace_time",
            "", "", "", "", "",
            "jbbinst_date_rcvd", "jbbinst_src_machine", "jbbinst_user", "jbbinst_location", "",
            "jbb_date_rcvd", "jbb_src_machine", "jbb_user", "jbb_location", "jbb_trace_time",
            "num_phases"};
        Logger.inform(header);
        Logger.inform(Util.listToString(fsOfInterest));
        return listTable(fsOfInterest, "test_case_data", true, null);
    }

    public boolean listPhaseStatus(String header, int tokenCnt) {
        String[] fsOfInterest = {"test_case_data", "phase_num",
            "inst_date_rcvd", "src_machine", "phase_user", "inst_location", "",
            "date_rcvd", "src_machine", "phase_user", "location", "trace_time"};
        Logger.inform(header);
        Logger.inform(Util.listToString(fsOfInterest));
        return listTable(fsOfInterest, "phase_status", true, null);
    }

    public int getDbid(TestCase testCase) {
        if (testCase == null) {
            return InvalidDBID;
        }
        String queryStr = "select * from test_case_data where "
                + "funding_agency = " + "'" + testCase.getAgency() + "' and "
                + "project = " + "'" + testCase.getProject() + "' and "
                + "round = " + testCase.getRound() + " and "
                + "application = " + "'" + testCase.getApplication() + "' and "
                + "dataset = " + "'" + testCase.getDataset() + "' and "
                + "cpu_count = " + testCase.getCpu();
        int retValue = InvalidDBID;
        int rowCount = 0;
        try {
            Statement st = postgresDB.createStatement();
            ResultSet rs = st.executeQuery(queryStr);
            while (rs.next()) {
                retValue = rs.getInt("dbid");
                rowCount++;
            }
            rs.close();
            st.close();
        } catch (Exception e) {
            Logger.warn("Can not list the test case data for dbid\n" + e);
            return InvalidDBID;
        }

        assert ((rowCount == 0) || (rowCount == 1));
        return retValue;
    }

    public boolean existsTestCase(TestCase testCase) {
        return (getDbid(testCase) != InvalidDBID);
    }

    public int getDefaultPIdx(int machIdx) {
        Object value = existsRow(machIdx, "base_resource", "default_profiles", "machine_profile");
        if (value == null) {
            return InvalidDBID;
        }
        assert (value instanceof Integer);
        return ((Integer) value).intValue();
    }

    public int getMemoryPIdx(int profIdx) {
        Object value = existsRow(profIdx, "dbid", "machine_profiles", "memory_benchmark_data");
        if (value == null) {
            return InvalidDBID;
        }
        assert (value instanceof Integer);
        return ((Integer) value).intValue();
    }

    public int getNetworkPIdx(int profIdx) {
        Object value = existsRow(profIdx, "dbid", "machine_profiles", "network_benchmark_data");
        if (value == null) {
            return InvalidDBID;
        }
        assert (value instanceof Integer);
        return ((Integer) value).intValue();
    }

    public int getCacheSysId(int profIdx) {
        Object value = existsRow(profIdx, "dbid", "machine_profiles", "cache_structure");
        if (value == null) {
            return InvalidDBID;
        }
        assert (value instanceof Integer);
        return ((Integer) value).intValue();
    }

    public int getBaseResource(int profIdx) {
        Object value = existsRow(profIdx, "dbid", "machine_profiles", "base_resource");
        if (value == null) {
            return InvalidDBID;
        }
        assert (value instanceof Integer);
        return ((Integer) value).intValue();
    }

    public String getMachineLabel(int profIdx) {
        Object value = existsRow(profIdx, "dbid", "machine_profiles", "nickname");
        if (value == null) {
            return null;
        }
        assert (value instanceof String);
        return (String) value;
    }

    public String getBaseResourceName(int profIdx) {
        Object value = existsRow(getBaseResource(profIdx), "dbid", "base_resources", "nickname");
        if (value == null) {
            return null;
        }
        assert (value instanceof String);
        return (String) value;
    }

    public double getBaseResourceFLOPS(int machIdx) {
        Object speed = existsRow(machIdx, "dbid", "base_resources", "processor_speed_hz");
        Object flopPC = existsRow(machIdx, "dbid", "base_resources", "flop_per_cycle");

        if ((speed == null) || (flopPC == null)) {
            return -1.0;
        }

        assert (speed instanceof Double);
        assert (flopPC instanceof Integer);

        return (((Double) speed).doubleValue() * ((Integer) flopPC).intValue());
    }

//LAURA START
    public int getBaseResourceCoresPerNode(int machIdx) {
        Object cores = existsRow(machIdx, "dbid", "base_resources", "processors_per_node");

        if (cores == null)  {
            return -1;
        }

        assert (cores instanceof Integer);

        return (((Integer) cores).intValue());
    }
//LAURA END
    public int getDbid(String resourceName) {
        Object dbid = existsRow(resourceName, "nickname", "base_resources", "dbid");
        if (dbid == null) {
            return InvalidDBID;
        }
        assert (dbid instanceof Integer);
        return ((Integer) dbid).intValue();
    }

    public String getBaseResourceString(int machIdx) {
        String queryStr = "select * from base_resources where dbid = " + machIdx;
        int rowCount = 0;
        String retValue = null;

        try {
            Statement st = postgresDB.createStatement();
            ResultSet rs = st.executeQuery(queryStr);
            while (rs.next()) {
                retValue = rs.getString("center") + "_"
                        + rs.getString("vendor") + "_"
                        + rs.getString("processor_type") + "_"
                        + rs.getString("processor_count") + "_"
                        + rs.getString("processors_per_node") + "_"
                        + rs.getString("processor_speed_hz") + "_"
                        + rs.getString("flop_per_cycle");
                rowCount++;
            }
            rs.close();
            st.close();

        } catch (Exception e) {
            Logger.warn("Can not list base resources\n" + e);
            return null;
        }

        assert ((rowCount == 0) || (rowCount == 1));
        return retValue;
    }

    public double getActualRuntime(TestCase tCase, int profIdx) {

        int testDbid = getDbid(tCase);
        int baseResIdx = getBaseResource(profIdx);

        if ((testDbid == InvalidDBID) || (baseResIdx == InvalidDBID)) {
            return -1.0;
        }

        assert ((testDbid != InvalidDBID) && (baseResIdx != InvalidDBID));
        Object[] vals = new Object[2];
        String[] cols = new String[2];
        vals[0] = new Integer(testDbid);
        vals[1] = new Integer(baseResIdx);
        cols[0] = "test_case_data";
        cols[1] = "base_resource";

        Object runtime = existsRow(vals, cols, "actual_runtimes", "runtime");
        if (runtime == null) {
            return -1.0;
        }
        assert (runtime instanceof Double);
        return ((Double) runtime).doubleValue();
    }

    public Object[] getMachineProfile(int profIdx) {
        String[] selectCols = new String[6];
        selectCols[0] = "base_resource";
        selectCols[1] = "memory_benchmark_data";
        selectCols[2] = "network_benchmark_data";
        selectCols[3] = "cache_structure";
        selectCols[4] = "nickname";
        selectCols[5] = "dbid";

        Object[] tokens = existsRow(profIdx, "dbid", "machine_profiles", selectCols);
        if (tokens == null) {
            return null;
        }
        return tokens;
    }

    public Object[] getNetworkProfile(int nprofIdx) {
        String[] selectCols = new String[9];
        selectCols[0] = "off_node_network_bandwidth";
        selectCols[1] = "off_node_network_latency";
        selectCols[2] = "on_node_network_bandwidth";
        selectCols[3] = "on_node_network_latency";
        selectCols[4] = "processors_per_node";
        selectCols[5] = "network_bus_count";
        selectCols[6] = "input_link_count";
        selectCols[7] = "output_link_count";
        selectCols[8] = "dbid";

        Object[] tokens = existsRow(nprofIdx, "dbid", "network_benchmark_data", selectCols);
        if (tokens == null) {
            return null;
        }
        return tokens;
    }

    public double getSimMemoryTime(TestCase testCase, int basePIdx) {

        int testDbid = getDbid(testCase);
        int baseResIdx = getBaseResource(basePIdx);

        if ((testDbid == InvalidDBID) || (baseResIdx == InvalidDBID)) {
            return -1.0;
        }

        assert ((testDbid != InvalidDBID) && (baseResIdx != InvalidDBID));
        String queryStr = "select A.ratio,A.machine_profile,A.simulated_cpu_time_max "
                + "from prediction_data A,prediction_runs B "
                + "where B.test_case_data = " + testDbid + " and A.prediction_run = B.dbid and "
                + "B.prediction_group = " + getSimMemoryTimeGroup()
                + " and simulated_cpu_time_max > 0.0 order by A.dbid";
        Logger.inform("Looking for " + testCase + " and " + basePIdx + " with " + queryStr);
        double retValue = -1.0;
        try {
            Statement st = postgresDB.createStatement();
            ResultSet rs = st.executeQuery(queryStr);
            while (rs.next()) {
                Object value = rs.getObject("ratio");
                if (value == null) {
                    continue;
                }
                assert (value instanceof Double);
                Double rat = (Double) value;
                if (rat.doubleValue() != 1.0) {
                    continue;
                }

                value = rs.getObject("machine_profile");
                if (value == null) {
                    continue;
                }
                assert (value instanceof Integer);
                Integer pro = (Integer) value;
                if (pro.intValue() == InvalidDBID) {
                    continue;
                }
                int currRes = getBaseResource(pro.intValue());
                if ((currRes == InvalidDBID) || (currRes != baseResIdx)) {
                    continue;
                }

                value = rs.getObject("simulated_cpu_time_max");
                if (value == null) {
                    continue;
                }
                assert (value instanceof Double);
                Double tim = (Double) value;

                if (tim.doubleValue() != 0.0) {
                    retValue = tim.doubleValue();
                }

                Logger.inform("Found " + retValue + " for " + testCase + " and " + basePIdx);
            }
            rs.close();
            st.close();
        } catch (Exception e) {
            Logger.warn("Can not list the table \"prediction_runs\" completely\n" + e);
        }
        return retValue;
    }

    public boolean listPredictionRun(String header, int tokenCnt) {
        String[] fsOfInterest = {"B.prediction_group", "B.machine_profile", "A.machine_profile",
            "B.test_case_data", "A.memory_time", "A.ratio", "A.predicted_runtime",
            "A.simulated_cpu_time_max", "", "", "B.bandwidth_calculation_method",
            "B.use_sim_memtime", "B.shortname", "A.date_run"};
        Logger.inform(header);
        Logger.inform(Util.listToString(fsOfInterest));
        String queryStr = "select ";
        for (int i = 1; i <= fsOfInterest.length; i++) {
            if (fsOfInterest[i - 1].length() > 0) {
                queryStr += fsOfInterest[i - 1];
                if (i != fsOfInterest.length) {
                    queryStr += ",";
                }
            }
        }
        queryStr += " from prediction_data A,prediction_runs B where A.prediction_run = B.dbid order by A.dbid";

        try {
            Statement st = postgresDB.createStatement();
            ResultSet rs = st.executeQuery(queryStr);
            while (rs.next()) {
                String tmp = "";
                for (int i = 0, j = 0; i < fsOfInterest.length; i++) {
                    Object val = null;
                    if ((fsOfInterest[i] != null) && (fsOfInterest[i].length() != 0)) {
                        val = rs.getObject(i + 1 - j);
                    } else {
                        j++;
                    }
                    if (val != null) {
                        tmp += val;
                    }
                    tmp += " | ";
                }
                Logger.inform(tmp);
            }
            rs.close();
            st.close();
        } catch (Exception e) {
            Logger.warn("Can not list the table \"prediction_runs\" completely\n" + e);
            return false;
        }
        return true;
    }

    public TreeMap getTestCaseUsers(TestCase tCase) {
        String[] statusFields = {"executable_user",
            "jbbinst_user",
            "jbb_user",
            "network_trace_user",
            "num_phases"};
        String[] phaseFields = {"phase_user"};

        int testDbid = getDbid(tCase);
        if (testDbid == InvalidDBID) {
            Logger.warn(tCase + " is not in the database");
            return null;
        }
        Object[] tokens = existsRow(testDbid, "dbid", "test_case_data", statusFields);
        if (tokens == null) {
            return null;
        }

        TreeMap retValue = new TreeMap();

        if (tokens[0] != null) {
            assert (tokens[0] instanceof String);
            retValue.put("ExecUser", "" + tokens[0]);
        }
        if (tokens[1] != null) {
            assert (tokens[1] instanceof String);
            retValue.put("JbbInstUser", "" + tokens[1]);
        }
        if (tokens[2] != null) {
            assert (tokens[2] instanceof String);
            retValue.put("JbbCollUser", "" + tokens[2]);
        }
        if (tokens[3] != null) {
            assert (tokens[3] instanceof String);
            retValue.put("NetTrcUser", "" + tokens[3]);
        }

        if (tokens[4] != null) {
            assert (tokens[4] instanceof Integer);
            int phsCnt = ((Integer) tokens[4]).intValue();
            for (int phsNo = 1; phsNo <= phsCnt; phsNo++) {
                Object[] selVals = new Object[2];
                selVals[0] = new Integer(testDbid);
                selVals[1] = new Integer(phsNo);
                String[] selCols = new String[2];
                selCols[0] = "test_case_data";
                selCols[1] = "phase_num";
                tokens = existsRow(selVals, selCols, "phase_status", phaseFields);
                if (tokens != null) {
                    retValue.put("PhaseUser_" + phsNo, "" + tokens[0]);
                }
            }
        }

        return retValue;
    }

    public TreeMap getTraceStatuses(TestCase tCase) {
        String[] statusFields = {"executable_date_rcvd", "executable_filename",
            "network_trace_date_rcvd", "network_trace_location",
            "jbbinst_date_rcvd", "jbbinst_location",
            "jbb_date_rcvd", "jbb_location",
            "num_phases", "network_trace_type"};
        String[] phaseFields = {"inst_date_rcvd", "inst_location", "date_rcvd", "location"};

        int testDbid = getDbid(tCase);
        if (testDbid == InvalidDBID) {
            Logger.warn(tCase + " is not in the database");
            return null;
        }
        Object[] tokens = existsRow(testDbid, "dbid", "test_case_data", statusFields);
        if (tokens == null) {
            return null;
        }

        int recIdx = 1;
        TreeMap retValue = new TreeMap();

        retValue.put((recIdx++) + ".Exec", "" + tokens[0] + ":" + tokens[1]);

        if (tokens[3] != null) {
            assert (tokens[3] instanceof String);
            String netTrcFile = (String) tokens[3];
            assert (tokens[9] instanceof Integer);
            int netTrcType = ((Integer) tokens[9]).intValue();
            if (netTrcType == psinsTraceTypeDbid()) {
                retValue.put((recIdx++) + ".Psins", "" + tokens[2] + ":" + tokens[3]);
                retValue.put((recIdx++) + ".Trf", "null:null");
            } else if (netTrcType == mpidTraceTypeDbid()) {
                retValue.put((recIdx++) + ".Trf", "" + tokens[2] + ":" + tokens[3]);
                retValue.put((recIdx++) + ".Psins", "null:null");
            }
        } else {
            retValue.put((recIdx++) + ".Psins", "null:null");
            retValue.put((recIdx++) + ".Trf", "null:null");
        }

        retValue.put((recIdx++) + ".JbbInst", "" + tokens[4] + ":" + tokens[5]);
        retValue.put((recIdx++) + ".JbbColl", "" + tokens[6] + ":" + tokens[7]);

        if (tokens[8] != null) {
            assert (tokens[8] instanceof Integer);
            int phsCnt = ((Integer) tokens[8]).intValue();
            retValue.put((recIdx++) + ".Phase", "" + phsCnt);
            for (int phsNo = 1; phsNo <= phsCnt; phsNo++) {
                Object[] selVals = new Object[2];
                selVals[0] = new Integer(testDbid);
                selVals[1] = new Integer(phsNo);
                String[] selCols = new String[2];
                selCols[0] = "test_case_data";
                selCols[1] = "phase_num";
                tokens = existsRow(selVals, selCols, "phase_status", phaseFields);
                if (tokens != null) {
                    retValue.put((recIdx++) + ".SimInst " + phsNo, "" + tokens[0] + ":" + tokens[1]);
                    retValue.put((recIdx++) + ".SimColl " + phsNo, "" + tokens[2] + ":" + tokens[3]);
                }
            }
        } else {
            retValue.put((recIdx++) + ".Phase", "0");
        }

        return retValue;
    }

    public String[] getTraceFilePaths(TestCase tCase, String type) {
        String[] retValue = null;

        String[] statusFields = {"network_trace_location", "jbbinst_location", "jbb_location", "num_phases"};
        String[] phaseFields = {"inst_location", "location"};

        int testDbid = getDbid(tCase);
        if (testDbid == InvalidDBID) {
            Logger.warn(tCase + " is not in the database");
            return null;
        }
        Object[] tokens = existsRow(testDbid, "dbid", "test_case_data", statusFields);
        if (tokens == null) {
            Logger.warn(testDbid + " does not exist");
            return null;
        }

        if (type.equals("jbbinst")) {
            if (tokens[1] == null) {
                return null;
            }
            assert (tokens[1] instanceof String);
            String path = (String) tokens[1];
            if (!Util.isFile(path)) {
                return null;
            }
            retValue = new String[1];
            retValue[0] = path;
        } else if (type.equals("jbbcoll")) {
            if (tokens[2] == null) {
                return null;
            }
            assert (tokens[2] instanceof String);
            String path = (String) tokens[2];
            if (!Util.isFile(path)) {
                return null;
            }
            retValue = new String[1];
            retValue[0] = path;
        } else if (type.equals("siminst") || type.equals("simcoll")) {
            if (tokens[3] == null) {
                return null;
            }
            assert (tokens[3] instanceof Integer);
            int phsCnt = ((Integer) tokens[3]).intValue();
            if (phsCnt == 0) {
                return null;
            }
            retValue = new String[phsCnt];
            for (int phsNo = 1; phsNo <= phsCnt; phsNo++) {
                Object[] selVals = new Object[2];
                selVals[0] = new Integer(testDbid);
                selVals[1] = new Integer(phsNo);
                String[] selCols = new String[2];
                selCols[0] = "test_case_data";
                selCols[1] = "phase_num";
                tokens = existsRow(selVals, selCols, "phase_status", phaseFields);
                if (tokens == null) {
                    return null;
                }
                String path = null;
                if (type.equals("siminst")) {
                    if (tokens[0] == null) {
                        return null;
                    }
                    assert (tokens[0] instanceof String);
                    path = (String) tokens[0];
                    if (!Util.isFile(path)) {
                        return null;
                    }
                    retValue[phsNo - 1] = path;
                } else {
                    if (tokens[1] == null) {
                        return null;
                    }
                    assert (tokens[1] instanceof String);
                    path = (String) tokens[1];
                    if (!Util.isFile(path)) {
                        return null;
                    }
                    retValue[phsNo - 1] = path;
                }
            }
        }
        return retValue;
    }

    public boolean listMemoryBenchmarkData(String header, int tokenCnt) {
        String queryStr = "select * from memory_benchmark_data order by dbid";
        try {
            Statement st = postgresDB.createStatement();
            ResultSet rs = st.executeQuery(queryStr);
            ResultSetMetaData meta = rs.getMetaData();

            while (rs.next()) {
                Object value = rs.getObject("memory_parameter_names");
                if (value == null) {
                    continue;
                }
                assert (value instanceof Integer);
                int parameterNameType = ((Integer) value).intValue();

                String tmp = "" + rs.getObject("dbid") + " | ";
                if (parameterNameType == paramExppenDropDbid()) {
                    tmp += "BWexppenDrop | ";
                    if (rs.getObject("parameter3") == null) {
                        tmp += "2 | ";
                    } else {
                        tmp += "3 | ";
                    }
                } else if (parameterNameType == paramStretchedExpDbid()) {
                    tmp += "BWstretchedExp | ";
                    if (rs.getObject("parameter2") == null) {
                        tmp += "2 | ";
                    } else {
                        tmp += "3 | ";
                    }
                } else if (parameterNameType == paramStretchedHitDbid()) {
                    tmp += "BWstretchedHit | ";
                    if (rs.getObject("parameter2") == null) {
                        tmp += "2 | ";
                    } else {
                        tmp += "3 | ";
                    }
                } else if (parameterNameType == paramStretchedEMultDbid()) {
                    tmp += "BWstretchedEMult | ";
                    if (rs.getObject("parameter3") == null) {
                        tmp += "2 | ";
                    } else {
                        tmp += "3 | ";
                    }
                } else if (parameterNameType == paramStretchedPenDbid()) {
                    tmp += "BWstretchedPen | ";
                    if (rs.getObject("parameter2") == null) {
                        tmp += "2 | ";
                    } else {
                        tmp += "3 | ";
                    }
                } else if (parameterNameType == paramCyclesDropDbid()) {
                    tmp += "BWcyclesDrop | ";
                    if (rs.getObject("parameter3") == null) {
                        tmp += "2 | ";
                    } else {
                        tmp += "3 | ";
                    }
                } else {
                    continue;
                }
                for (int i = 2; i <= meta.getColumnCount() - 2; i++) {
                    value = rs.getObject(i);
                    if (value != null) {
                        tmp += (value + " | ");
                    }
                }
                Logger.inform(tmp);
            }
            rs.close();
            st.close();
        } catch (Exception e) {
            Logger.warn("Can not list memory benchmark data\n" + e);
            return false;
        }
        return true;
    }

    public Object[] getMemoryProfile(int mprofIdx) {
        Object[] tokens = existsRow(mprofIdx, "dbid", "memory_benchmark_data", (String[]) null);
        if (tokens == null) {
            return null;
        }

        assert (tokens[15] instanceof Integer);
        int parameterNameType = ((Integer) tokens[15]).intValue();

        String meth = "";
        int lvlCnt = 3;
        int fieldCnt = 0;
        if (parameterNameType == paramExppenDropDbid()) {
            meth = "BWexppenDrop";
            if (tokens[3] == null) {
                lvlCnt = 2;
            }
            fieldCnt = ((lvlCnt + 1) * 2) + (2 * 2);
        } else if (parameterNameType == paramStretchedExpDbid()) {
            meth = "BWstretchedExp";
            if (tokens[2] == null) {
                lvlCnt = 2;
            }
            fieldCnt = lvlCnt * 3;
        } else if (parameterNameType == paramStretchedHitDbid()) {
            meth = "BWstretchedHit";
            if (tokens[2] == null) {
                lvlCnt = 2;
            }
            fieldCnt = (lvlCnt * 3) + (lvlCnt - 1);
        } else if (parameterNameType == paramStretchedEMultDbid()) {
            meth = "BWstretchedEMult";
            if (tokens[3] == null) {
                lvlCnt = 2;
            }
            fieldCnt = (lvlCnt * 2) + 1;
        } else if (parameterNameType == paramStretchedPenDbid()) {
            meth = "BWstretchedPen";
            if (tokens[2] == null) {
                lvlCnt = 2;
            }
            fieldCnt = (lvlCnt * 3) + 1 + (lvlCnt - 1);
        } else if (parameterNameType == paramCyclesDropDbid()) {
            meth = "BWcyclesDrop";
            if (tokens[3] == null) {
                lvlCnt = 2;
            }
            fieldCnt = lvlCnt + 1;
        } else {
            return null;
        }

        Object[] retValue = new Object[fieldCnt + 4];
        int idx = 0;
        retValue[idx++] = meth;
        retValue[idx++] = new Integer(lvlCnt);
        for (int i = 1; i <= 12; i++) {
            if (tokens[i] != null) {
                assert (tokens[i] instanceof Double);
                retValue[idx++] = tokens[i];
            }
        }
        if (tokens[13] != null) {
            assert (tokens[13] instanceof String);
            retValue[idx++] = tokens[13];
        } else {
            retValue[idx++] = "";
        }
        retValue[idx++] = new Integer(mprofIdx);
        assert (idx == (fieldCnt + 4));

        return retValue;
    }

    public int updateTraceStatus(int testDbid, int phsCnt) {
        if (phsCnt <= 0) {
            Logger.warn("Phase count " + phsCnt + " is invalid in updating trace status");
            return InvalidDBID;
        }
        if (!existsTestCase(testDbid)) {
            Logger.warn("Test case " + testDbid + " does not exist");
            return InvalidDBID;
        }
        int dbid = updateRow(new Integer(testDbid), "dbid", "test_case_data", new Integer(phsCnt), "num_phases");
        if (dbid == InvalidDBID) {
            Logger.warn("Update failed for test_case_data");
            return InvalidDBID;
        }
        return dbid;
    }

    public int updateTraceStatus(int testDbid, String typ, String date, String mach, String user, String file, double eTime) {
        if (!existsTestCase(testDbid)) {
            Logger.warn("Test case " + testDbid + " does not exist");
            return InvalidDBID;
        }
        if ((typ == null) || (date == null) || (mach == null) || (user == null) || (file == null)) {
            Logger.warn("One or more field is not valid fo trace status update");
            return InvalidDBID;
        }

        int idx = 0;
        Object[] tokens = new Object[6];
        tokens[idx++] = date;
        tokens[idx++] = mach;
        tokens[idx++] = user;
        tokens[idx++] = file;

        String[] valCols = null;
        if (typ.equals("exec")) {
            String[] columns = {"executable_date_rcvd", "executable_src_machine", "executable_user", "executable_filename"};
            valCols = columns;
        } else if (typ.equals("psins")) {
            String[] columns = {"network_trace_date_rcvd", "network_trace_src_machine", "network_trace_user",
                "network_trace_location", "network_trace_time", "network_trace_type"};
            tokens[idx++] = new Double(eTime);
            tokens[idx++] = new Integer(psinsTraceTypeDbid());
            valCols = columns;
        } else if (typ.equals("mpidtrace")) {
            String[] columns = {"network_trace_date_rcvd", "network_trace_src_machine", "network_trace_user",
                "network_trace_location", "network_trace_time", "network_trace_type"};
            tokens[idx++] = new Double(eTime);
            tokens[idx++] = new Integer(mpidTraceTypeDbid());
            valCols = columns;
        } else if (typ.equals("jbbinst")) {
            String[] columns = {"jbbinst_date_rcvd", "jbbinst_src_machine", "jbbinst_user", "jbbinst_location"};
            valCols = columns;
        } else if (typ.equals("jbbcoll")) {
            String[] columns = {"jbb_date_rcvd", "jbb_src_machine", "jbb_user", "jbb_location", "jbb_trace_time"};
            tokens[idx++] = new Double(eTime);
            valCols = columns;
        } else {
            Logger.warn("Updating trace status has seen unknown type " + typ);
            return InvalidDBID;
        }
        int dbid = updateRow(new Integer(testDbid), "dbid", "test_case_data", tokens, valCols);
        if (dbid == InvalidDBID) {
            Logger.warn("Update failed for test_case_data");
            return InvalidDBID;
        }
        return dbid;
    }

    public int updatePhaseStatus(int testDbid, String typ, int phsNo,
            String date, String mach, String user, String file, double eTime) {
        if ((phsNo <= 0) || (typ == null) || (date == null) || (mach == null)
                || (user == null) || (file == null)) {
            Logger.warn("One or more field is not valid fo trace status update");
            return InvalidDBID;
        }
        if (!existsTestCase(testDbid)) {
            Logger.warn("Test case " + testDbid + " does not exist");
            return InvalidDBID;
        }

        int phaseStatDbid = InvalidDBID;

        Object[] selVals = new Object[2];
        selVals[0] = new Integer(testDbid);
        selVals[1] = new Integer(phsNo);
        String[] selCols = new String[2];
        selCols[0] = "test_case_data";
        selCols[1] = "phase_num";
        Object existDbid = existsRow(selVals, selCols, "phase_status", "dbid");
        if (existDbid == null) {
            phaseStatDbid = insertRow(selVals, selCols, "phase_status");
        } else {
            phaseStatDbid = ((Integer) existDbid).intValue();
        }

        assert (phaseStatDbid != InvalidDBID);

        int idx = 0;
        Object[] tokens = new Object[5];
        tokens[idx++] = date;
        tokens[idx++] = mach;
        tokens[idx++] = user;
        tokens[idx++] = file;

        String[] valCols = null;
        if (typ.equals("siminst")) {
            String[] columns = {"inst_date_rcvd", "src_machine", "phase_user", "inst_location"};
            valCols = columns;
        } else if (typ.equals("simcoll")) {
            tokens[idx++] = new Double(eTime);
            String[] columns = {"date_rcvd", "src_machine", "phase_user", "location", "trace_time"};
            valCols = columns;
        } else {
            Logger.warn("Updating phase status has seen unknown type " + typ);
            return InvalidDBID;
        }

        int dbid = updateRow(new Integer(phaseStatDbid), "dbid", "phase_status", tokens, valCols);
        if (dbid == InvalidDBID) {
            Logger.warn("Update failed for phase_status");
            return InvalidDBID;
        }
        return dbid;
    }

    public int addPredictionRun(String[] fields) {
        Object[] tokens = Util.stringsToRecord(fields, predictionRunsSignature);
        if (tokens == null) {
            Logger.warn("One or more entry in prediction run data is invalid " + Util.listToString(fields));
            return InvalidDBID;
        }

        boolean status = existsPredGroup(((Integer) tokens[0]).intValue())
                && existsProfile(((Integer) tokens[1]).intValue())
                && existsProfile(((Integer) tokens[2]).intValue())
                && existsTestCase(((Integer) tokens[3]).intValue());
        if (!status) {
            Logger.warn("The prediction run record has invalid value(s)");
            return InvalidDBID;
        }

        try {
            Object[] selVals = new Object[4];
            selVals[0] = tokens[0];
            selVals[1] = tokens[1];
            selVals[2] = tokens[3];
            selVals[3] = tokens[12];
            String[] selCols = {"prediction_group", "machine_profile", "test_case_data", "shortname"};
            Integer runDbid = (Integer) existsRow(selVals, selCols, "prediction_runs", "dbid");
            if (runDbid == null) {
                int bandwithMethd = InvalidDBID;
                int memoryPIdx = getMemoryPIdx(((Integer) tokens[1]).intValue());
                assert (memoryPIdx != InvalidDBID);

                Object[] memoryPTokens = getMemoryProfile(memoryPIdx);
                assert (memoryPTokens != null);

                if (((String) memoryPTokens[0]).equals("BWexppenDrop")) {
                    bandwithMethd = methExppenDropDbid();
                } else if (((String) memoryPTokens[0]).equals("BWstretchedExp")
                        || ((String) memoryPTokens[0]).equals("ti09")) {
                    bandwithMethd = methStretchedExpDbid();
                } else if (((String) memoryPTokens[0]).equals("BWstretchedHit")) {
                    bandwithMethd = methStretchedExpDbid();
                } else if (((String) memoryPTokens[0]).equals("BWstretchedEMult")) {
                    bandwithMethd = methStretchedExpDbid();
                } else if (((String) memoryPTokens[0]).equals("BWstretchedPen")) {
                    bandwithMethd = methStretchedExpDbid();
                } else if (((String) memoryPTokens[0]).equals("BWcyclesDrop")) {
                    bandwithMethd = methStretchedExpDbid();
                } else {
                    Logger.error("Unknown parameter names " + memoryPTokens[14]
                            + " for memory profile " + memoryPIdx);
                }
                String[] insCols = {"test_case_data", "prediction_group", "shortname", "bandwidth_calculation_method",
                    "machine_profile", "ratio", "use_sim_memtime", "run_convolver_start"};
                Object[] insVals = new Object[8];
                insVals[0] = tokens[3];
                insVals[1] = tokens[0];
                insVals[2] = tokens[12];
                insVals[3] = new Integer(bandwithMethd);
                insVals[4] = tokens[1];
                insVals[5] = tokens[5];
                insVals[6] = tokens[11];
                insVals[7] = tokens[13];

                int dbid = insertRow(insVals, insCols, "prediction_runs");
                assert (dbid != InvalidDBID);
                runDbid = new Integer(dbid);
            }

            String[] insCols = {"prediction_run", "date_run", "memory_time", "ratio", "predicted_runtime",
                "machine_profile", "simulated_cpu_time_max"};
            Object[] insVals = new Object[7];
            insVals[0] = runDbid;
            insVals[1] = tokens[13];
            insVals[2] = tokens[4];
            insVals[3] = tokens[5];
            insVals[4] = tokens[6];
            insVals[5] = tokens[2];
            insVals[6] = tokens[7];

            int dbid = insertRow(insVals, insCols, "prediction_data");
            if (dbid == InvalidDBID) {
                Logger.warn("Update failed for prediction_data");
                return InvalidDBID;
            }
            return dbid;
        } catch (Exception e) {
            Logger.debug(e);
            e.printStackTrace();
        }
        return -1;
    }

    public String[] getEmailsFromTestCase(TestCase testCase) {
        if (testCase == null) {
            return null;
        }
        TreeMap tuples = getTestCaseUsers(testCase);
        if (tuples == null){
            return null;
        }

        Iterator it = tuples.keySet().iterator();
        int i = 0;
        while (it.hasNext()) {
            String key = (String) it.next();
            String val = (String) tuples.get(key);
            if (Util.isValidEmail(val)){
                i++;
            }
        }

        String[] unqEmails = new String[i];
        it = tuples.keySet().iterator();
        i = 0;
        while (it.hasNext()) {
            String key = (String) it.next();
            String val = (String) tuples.get(key);
            if (Util.isValidEmail(val)){
                unqEmails[i++] = val;
            }
        }

        if (unqEmails.length == 0){
            return null;
        }

        return unqEmails;
    }

    /** the following routines are designed for GUI access mostly **/
    String[] getAllRowsFormatted(String tableName, String[] columns, String orderCol) {
        return getAllRowsFormatted(tableName, columns, orderCol, ' ');
    }

    String[] getAllRowsFormatted(String tableName, String[] columns, String orderCol, char filler) {
        String queryStr = null;
        if (tableName == null) {
            return null;
        }
        if (columns == null) {
            queryStr = "select * from " + tableName;
        } else {
            queryStr = "select ";
            for (int i = 0; i < columns.length; i++) {
                queryStr += columns[i];
                if (i != (columns.length - 1)) {
                    queryStr += ",";
                }
            }
            queryStr += " from " + tableName;
        }
        if (orderCol != null) {
            queryStr += " order by " + orderCol;
        }
        if (queryStr == null) {
            Logger.warn("Select query string is null");
            return null;
        }
        int rowCount = 0;
        TreeSet stringSet = new TreeSet();
        try {
            Statement st = postgresDB.createStatement();
            ResultSet rs = st.executeQuery(queryStr);
            ResultSetMetaData meta = rs.getMetaData();
            while (rs.next()) {
                String rowString = new String("");
                if (columns != null) {
                    for (int i = 0; i < columns.length; i++) {
                        if (i != 0) {
                            rowString += " | ";
                        }
                        Object v = rs.getObject(columns[i]);
                        rowString += Format.formatNd(v, filler);
                    }
                } else {
                    for (int i = 0; i < meta.getColumnCount(); i++) {
                        if (i != 0) {
                            rowString += " | ";
                        }
                        Object v = rs.getObject(i + 1) + " ";
                        rowString += Format.formatNd(v, filler);
                    }
                }
                stringSet.add(rowString);
                rowCount++;
            }
            rs.close();
            st.close();
        } catch (Exception e) {
            Logger.warn("Can not list the table \"" + tableName + "\"\n" + e);
            return null;
        }

        if (rowCount == 0) {
            return null;
        }
        Object[] eles = stringSet.toArray();
        String[] retValue = new String[eles.length];
        for (int i = 0; i < eles.length; i++) {
            retValue[i] = (String) eles[i];
        }
        return retValue;
    }

    public String[] getGUIResourceEntries() {
        String[] cols = {"dbid", "nickname"};
        return getAllRowsFormatted("base_resources", cols, "dbid");
    }

    public String[] getGUIPredGroupEntries() {
        String[] cols = {"dbid", "name"};
        return getAllRowsFormatted("prediction_groups", cols, "dbid");
    }

    public String[] getGUITestCases() {
        String[] cols = {"funding_agency", "project", "round", "application", "dataset", "cpu_count"};
        return getAllRowsFormatted("test_case_data", cols, null);
    }

    public boolean listCacheStructures() {
        String[] fsOfInterest = {"dbid", "l1_size", "l1_associativity", "l1_linesize", "l1_replacement_policy",
            "l2_size", "l2_associativity", "l2_linesize", "l2_replacement_policy", "l3_size", "l3_associativity",
            "l3_linesize", "l3_replacement_policy", "comments"};
        String header = "[sysid] [lvl_count] L1[size assoc line repl] L2[size assoc line repl] L3[size assoc line repl] [comments]";
        Logger.inform(header);
        String queryStr = "select * from cache_structures order by dbid";
        try {
            Statement st = postgresDB.createStatement();
            ResultSet rs = st.executeQuery(queryStr);
            while (rs.next()) {
                String tmp = "";
                for (int i = 0; i < fsOfInterest.length; i++) {
                    Object val = rs.getObject(fsOfInterest[i]);
                    if (val != null) {
                        tmp += val;
                    } else {
                        tmp += " ";
                    }
                    tmp += "|";
                }
                StringTokenizer tokens = new StringTokenizer(tmp, "|");
                int lastToken = tokens.countTokens();
                String levels = "1";
                if (rs.getObject("l3_size") != null) {
                    levels = "3";
                } else if (rs.getObject("l2_size") != null) {
                    levels = "2";
                }
                int i = 1;
                String output = "";
                String dbid = "";
                while (tokens.hasMoreTokens()) {
                    String token = tokens.nextToken();
                    if (i == 1) {
                        output += token.trim() + "\t" + levels;
                        dbid = token;
                    } else if (!token.contentEquals(" ")) {
                        if ((i == lastToken) && !token.trim().startsWith("#")) {
                            output += "\t" + "#" + token.trim();
                        } else {
                            if (levels.contentEquals("3") && (i == 6 || i == 10)) {
                                output += "\t" + Util.convertBytesToKb(token.trim());
                            } else if (levels.contentEquals("2") && i == 6) {
                                output += "\t" + Util.convertBytesToKb(token.trim());
                            } else if (i == 2) {
                                output += "\t" + Util.convertBytesToKb(token.trim());
                            }
                            output += "\t" + token.trim();
                        }
                    }
                    ++i;
                }
                Logger.inform(output);
            }
            rs.close();
            st.close();
        } catch (Exception e) {
            Logger.warn("Can not list the table \"cache_structures\" completely\n" + e);
            return false;
        }
        return true;
    }
}
