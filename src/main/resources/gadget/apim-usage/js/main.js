var chartSuccess = gadgetConfig;

$(function () {
    fetchData(drawChart)

});


/**
 * Fetching the data from the event store.
 * @param successCallback
 */
function fetchData(successCallback) {
    var timeFrom = new Date();
    timeFrom.setMonth(timeFrom.getMonth() - 1);
    timeFrom = timeFrom.getTime();

    var timeTo = new Date().getTime();

    $.ajax({
        url: "/portal/apis/apim-analytics?timeFrom=" + timeFrom + "&timeTo=" + timeTo,
        method: "GET",
        contentType: "application/json",
        success: function (data) {
            var tableData = data.message;
            if (successCallback !== null) {
                successCallback(data.message);
            }
        }
    });
}

/** To draw the chart */
function drawChart(data) {
    if (!data) {
        $("#chart-canvas").html("No chart data available");
    } else {
        //perform necessary transformation on input data
        chartSuccess.schema[0].data = chartSuccess.processData(data);
        //finally draw the chart on the given canvas
        chartSuccess.chartConfig.width = $("#chart-canvas").width() - 20;
        chartSuccess.chartConfig.height = $("#chart-canvas").height();

        var vg = new vizg(chartSuccess.schema, chartSuccess.chartConfig);

        $("#chart-canvas").empty();
        vg.draw("#chart-canvas");
    }
}
