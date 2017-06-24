/*
* Copyright (c) 2017, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
*
* WSO2 Inc. licenses this file to you under the Apache License,
* Version 2.0 (the "License"); you may not use this file except
* in compliance with the License.
* You may obtain a copy of the License at
*
* http://www.apache.org/licenses/LICENSE-2.0
*
* Unless required by applicable law or agreed to in writing,
* software distributed under the License is distributed on an
* "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
* KIND, either express or implied. See the License for the
* specific language governing permissions and limitations
* under the License.
*
*/

package sample.client;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.json.simple.JSONArray;
import sample.client.dto.APIUsageDTO;
import sample.client.exception.RDBMSClientException;
import sample.client.pojo.APIUsage;

import javax.naming.Context;
import javax.naming.InitialContext;
import javax.naming.NamingException;
import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Sample client to query the database and get analytics.
 */
public class RDBMSClient {

    private static final String DATA_SOURCE_NAME = "jdbc/WSO2AM_STATS_DB";
    private static volatile DataSource dataSource = null;
    private static final Log log = LogFactory.getLog(RDBMSClient.class);
    private static final Object lock = new Object();

    /**
     * Initializes the client
     * @throws RDBMSClientException RDBMS client exception will be thrown, if the datasource is not found.
     */
    public RDBMSClient() throws RDBMSClientException {
        try {
            synchronized (lock) {
                if (dataSource == null) {
                    Context ctx = new InitialContext();
                    dataSource = (DataSource) ctx.lookup(DATA_SOURCE_NAME);
                }
            }
        } catch (NamingException e) {
            String errorMsg = "Error while looking up the data  source: " + DATA_SOURCE_NAME;
            log.error(errorMsg, e);
            throw new RDBMSClientException(errorMsg, e);
        }
    }

    /**
     * Returns a list of APIUsageDTO objects that contain information related to APIs that
     * belong to a particular provider and the number of total API calls each API has processed
     * up to now. This method does not distinguish between different API versions. That is all
     * versions of a single API are treated as one, and their individual request counts are summed
     * up to calculate a grand total per each API.
     *
     * @return a List of APIUsageDTO objects - possibly empty
     * @throws RDBMSClientException if an error occurs
     *                              while contacting backend services
     */
    public List<APIUsageDTO> getProviderAPIUsage(long from, long end, int limit)
            throws RDBMSClientException {

        Collection<APIUsage> usageData = getAPIUsageData(APIUsageStatisticsClientConstants.API_VERSION_USAGE_SUMMARY,
                from, end);
        Map<String, APIUsageDTO> usageByAPIs = new TreeMap<String, APIUsageDTO>();
        for (APIUsage usage : usageData) {
            String[] apiData = { usage.getApiName(), usage.getApiVersion(), usage.getProvider() };

            JSONArray jsonArray = new JSONArray();
            jsonArray.add(0, apiData[0]);
            jsonArray.add(1, apiData[1]);
            jsonArray.add(2, apiData[2]);
            String apiName = jsonArray.toJSONString();
            APIUsageDTO usageDTO = usageByAPIs.get(apiName);
            if (usageDTO != null) {
                usageDTO.setCount(usageDTO.getCount() + usage.getRequestCount());
            } else {
                usageDTO = new APIUsageDTO();
                usageDTO.setApiName(usage.getApiName());
                usageDTO.setVersion(usage.getApiVersion());
                usageDTO.setProvider(usage.getProvider());
                usageDTO.setCount(usage.getRequestCount());
                usageByAPIs.put(apiName, usageDTO);
            }
        }
        return getAPIUsageTopEntries(new ArrayList<APIUsageDTO>(usageByAPIs.values()), limit);
    }

    /**
     * This method gets the usage data for a given API across all versions
     *
     * @param tableName name of the table in the database
     * @return a collection containing the API usage data
     * @throws RDBMSClientException if an error occurs while querying the database
     */
    private Collection<APIUsage> getAPIUsageData(String tableName, long from, long to)
            throws RDBMSClientException {
        Connection connection = null;
        PreparedStatement statement = null;
        ResultSet resultSet = null;
        Collection<APIUsage> usageDataList = new ArrayList<APIUsage>();
        String fromDate = formatDate(from);
        String toDate = formatDate(to);

        try {
            connection = dataSource.getConnection();
            String query;
            String filter = APIUsageStatisticsClientConstants.CONTEXT + " not like '%/t/%' ";

            query = "SELECT " + APIUsageStatisticsClientConstants.API + "," + APIUsageStatisticsClientConstants.CONTEXT
                    + "," + APIUsageStatisticsClientConstants.VERSION + ","
                    + APIUsageStatisticsClientConstants.API_PUBLISHER + "," + "SUM("
                    + APIUsageStatisticsClientConstants.TOTAL_REQUEST_COUNT + ") AS aggregateSum " + " FROM "
                    + tableName + " WHERE " + APIUsageStatisticsClientConstants.TIME + " BETWEEN ? AND ? AND " + filter
                    + " GROUP BY " + APIUsageStatisticsClientConstants.API + ","
                    + APIUsageStatisticsClientConstants.CONTEXT + "," + APIUsageStatisticsClientConstants.VERSION + ","
                    + APIUsageStatisticsClientConstants.API_PUBLISHER;
            statement = connection.prepareStatement(query);
            statement.setString(1, fromDate);
            statement.setString(2, toDate);

            resultSet = statement.executeQuery();
            while (resultSet.next()) {
                String apiName = resultSet.getString(APIUsageStatisticsClientConstants.API);
                String context = resultSet.getString(APIUsageStatisticsClientConstants.CONTEXT);
                String version = resultSet.getString(APIUsageStatisticsClientConstants.VERSION);
                String provider = resultSet.getString(APIUsageStatisticsClientConstants.API_PUBLISHER);
                long requestCount = resultSet.getLong("aggregateSum");
                usageDataList.add(new APIUsage(apiName, context, version, provider, requestCount));
            }
        } catch (SQLException e) {
            throw new RDBMSClientException("Error occurred while querying API usage data from JDBC database", e);
        } finally {
            closeDatabaseLinks(resultSet, statement, connection);
        }
        return usageDataList;
    }

    private String formatDate(long date) {
        Date dte = new Date(date);
        SimpleDateFormat formatter = new SimpleDateFormat("yyyy-MM-dd HH:mm");
        return formatter.format(dte);
    }

    /**
     * This method sort and limit the result size for API usage data
     *
     * @param usageData data to be sort and limit
     * @param limit     value to be limited
     * @return list of APIUsageDTO
     */
    private List<APIUsageDTO> getAPIUsageTopEntries(List<APIUsageDTO> usageData, int limit) {
        Collections.sort(usageData, new Comparator<APIUsageDTO>() {
            public int compare(APIUsageDTO o1, APIUsageDTO o2) {
                // Note that o2 appears before o1
                // This is because we need to sort in the descending order
                return (int) (o2.getCount() - o1.getCount());
            }
        });
        if (usageData.size() > limit) {
            APIUsageDTO other = new APIUsageDTO();
            other.setApiName("[\"Other\"]");
            for (int i = limit; i < usageData.size(); i++) {
                other.setCount(other.getCount() + usageData.get(i).getCount());
            }
            while (usageData.size() > limit) {
                usageData.remove(limit);
            }
            usageData.add(other);
        }
        return usageData;
    }

    /**
     * This method is used to close the ResultSet, PreparedStatement and Connection after getting data from the DB
     * This is called if a "PreparedStatement" is used to fetch results from the DB
     *
     * @param resultSet         ResultSet returned from the database query
     * @param preparedStatement prepared statement used in the database query
     * @param connection        DB connection used to get data from the database
     */
    private void closeDatabaseLinks(ResultSet resultSet, PreparedStatement preparedStatement, Connection connection) {

        if (resultSet != null) {
            try {
                resultSet.close();
            } catch (SQLException e) {
                //this is logged and the process is continued because the query has executed
                log.error("Error occurred while closing the result set from JDBC database.", e);
            }
        }
        if (preparedStatement != null) {
            try {
                preparedStatement.close();
            } catch (SQLException e) {
                //this is logged and the process is continued because the query has executed
                log.error("Error occurred while closing the prepared statement from JDBC database.", e);
            }
        }
        if (connection != null) {
            try {
                connection.close();
            } catch (SQLException e) {
                //this is logged and the process is continued because the query has executed
                log.error("Error occurred while closing the JDBC database connection.", e);
            }
        }
    }
}
