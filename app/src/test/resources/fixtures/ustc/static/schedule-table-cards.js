define([
  'models/card',
  'services/cardsService'
], function (Card, CardsService) {
  'use strict';

  var CardList = Backbone.Collection.extend({
    model: Card,

    comparator: function(card1, card2) {
      if (card1.get('startTime') < card2.get('startTime')) return -1;
      if (card1.get('startTime') > card2.get('startTime')) return 1;
      if (card1.get('endTime') < card2.get('endTime')) return -1;
      if (card1.get('endTime') > card2.get('endTime')) return 1;
      return 0;
    },

    setWeekIndices: function() {
      var _self = this;
      var weekIndicesDigestParams = [];
      _.each(this.models, function(model) {
        weekIndicesDigestParams.push({weekIndicesGroupId: model.cid, weekIndices: model.get('weekIndexes')});
      });
      var weekIndexObj = CardsService.getWeekIndices(weekIndicesDigestParams);

      $.each(_.keys(weekIndexObj), function() {
        _self.get(this).set('weekIndices', weekIndexObj[this]);
      });
    }
  });

  return new CardList();
});
