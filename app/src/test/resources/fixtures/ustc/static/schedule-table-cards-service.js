define([], function () {

  var cardService = {

    getWeekIndices: function (weekIndicesDigestParams) {
      var weekIndexObj = [];
      $.ajax({
        url : window.CONTEXT_PATH + "/ws/schedule-table/week-indices-digest",
        type: 'post',
        contentType : 'application/json',
        async: false,
        data: JSON.stringify(weekIndicesDigestParams),
        success: function(res) {
          weekIndexObj = res.result;
        }
      });
      return weekIndexObj;
    }
  }

  return cardService;
});