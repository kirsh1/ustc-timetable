/*global define*/
define([
  'collections/schedules',
  'services/cardsService'
], function (Schedules, CardsService) {
  'use strict';

  var Card = Backbone.Model.extend({
    defaults: function () {
      return {
        schedules: new Schedules(),
        startTime: 0,
        endTime: 0,
        weekday: null,
        lessonId: null,
        scheduleGroupId: null,
        periods: 0,
        weekIndexes: [],
        weekIndices: '',
        changed: false,
        selected: false,
        lessonSelected: false,
        adminclassIds: []
      }
    },
    initBySchedules: function(schedules) {
      this.set('startTime', schedules[0].startTime);
      this.set('endTime', schedules[0].endTime);
      this.set('weekday', schedules[0].weekday);
      this.set('lessonId', schedules[0].lessonId);
      this.set('scheduleGroupId', schedules[0].scheduleGroupId);
      this.set('periods', schedules[0].periods);

      var self = this;
      _.each(schedules, function(schedule) {
        self.get('schedules').add(schedule);
        if (self.get('weekIndexes').indexOf(schedule.weekIndex) == -1) {
          self.get('weekIndexes').push(schedule.weekIndex);
        }
      });
    },
    //根据card里的schedule得到老师
    getTeachers: function() {
      var teachers = [];
      var teacherIds = [];
      _.each(this.get('schedules').models, function(schedule) {
        if (!schedule.get('teacherId') && !schedule.get('personId') && !schedule.get('personName')) {
          return;
        }
        if (teacherIds.indexOf(schedule.get('teacherId')) == -1) {
          teacherIds.push(schedule.get('teacherId'));
          teachers.push({
            id: schedule.get('teacherId'),
            personId: schedule.get('personId'),
            personName: schedule.get('personName')
          });
        }
      });
      return teachers;
    },
    setChanged: function() {
      this.set('changed', true);
    },
    setWeekIndices: function() {
      var weekIndicesDigestParams = [];
      weekIndicesDigestParams.push({weekIndicesGroupId: this.cid, weekIndices: this.get('weekIndexes')});
      var weekIndexObj = CardsService.getWeekIndices(weekIndicesDigestParams);
      this.set('weekIndices', weekIndexObj[this.cid]);
    }
  });

  return Card;
});
