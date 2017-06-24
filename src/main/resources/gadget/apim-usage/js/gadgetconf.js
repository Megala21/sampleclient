var gadgetConfig = {
    schema: [{
        "metadata": {
            "names": ["API", "Requests"],
            "types": ["ordinal", "linear"]
        },
        "data": []
    }],
    "chartConfig": {
        "charts": [{type: "arc", "x": "Requests", color: "API", mode: "donut"}],
        "xTitle": "Request Count",
        tooltip: {"enabled": true, "color": "#828b94", "type": "symbol", "content": ["API", "Requests"]},
        "yTitle": "Request Count",
        padding : {"top" : 30, "left" :0, "bottom" : 30, "right"  :150},
        "legendTitle" : "APIs",
        percentage: true,
        "width": 500,
        "height": 700
    },
    processData: function (data) {
        var result = [];
        data.forEach(function (row, i) {
            var value = row['Request_Count'];
            var key = row["Api"];

            result.push([key, value]);
        });
        return result;
    }
};