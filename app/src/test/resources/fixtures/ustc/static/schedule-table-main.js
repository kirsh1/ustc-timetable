+function ($) {
  'use strict';

  var TimeTable = function (element, options) {

    this.options = options;

    this.$el = $(element);

    require.config({
      baseUrl : window.CONTEXT_PATH + '/static/scheduletable/js',

      // 添加缓存破坏参数，确保每次发版后浏览器加载最新JS模块，避免版本不一致导致布局错乱
      // @Author: Jun.Wang @Date: 2026-05-25
      urlArgs: 'v=00004',

      paths: {
        text : window.CONTEXT_PATH + '/static/eams-ui/js/text'
      }
    });

    this.initLessons();
    this.initCards();
    this.initScheduleGroups();
    if (this.options.unDraggableTimeTable && this.options.semesterId) {
      this.unDraggableTimeTable();
    } else {
      this.initHolidays();
      this.getStartDateOfSemester();
      this.arrangeSchedule();
    }
  }

  TimeTable.VERSION = '0.0.1';

  TimeTable.prototype.initCards = function () {
    var self = this;
    require([
      'models/card',
      'collections/cards'
    ], function (CardModel, cardList) {
      var cardMap = _.groupBy(self.options.schedules, function(schedule) {
        return schedule.lessonId + ':' + schedule.scheduleGroupId + ':' +
          schedule.startTime + ':' + schedule.endTime + ':' + schedule.weekday;
      });
      var cards = _.values(cardMap);

      var lessonId2adminclassIds = {};
      $.each(self.options.lessons, function (index, lesson) {
        lessonId2adminclassIds[lesson.id] = lesson.adminclassIds;
      });

      _.each(cards, function(schedules) {
        var cardModel = new CardModel();
        cardModel.initBySchedules(schedules);
        cardModel.set("adminclassIds", lessonId2adminclassIds[schedules[0].lessonId]);
        cardList.add(cardModel);
      });
      cardList.setWeekIndices();
    });
  }

  TimeTable.prototype.initLessons = function() {
    var self = this;
    require([
      'models/lesson',
      'collections/lessons'
    ], function (LessonModel, lessonList) {
      $.each(self.options.lessons, function() {
        var lessonModel = new LessonModel(this);
        lessonList.add(lessonModel);
      });
    });
  }

  TimeTable.prototype.initScheduleGroups = function() {
    var self = this;
    require([
      'models/scheduleGroup',
      'collections/scheduleGroups'
    ], function (ScheduleGroupModel, scheduleGroups) {
      _.each(self.options.scheduleGroups, function(group) {
        var groupModel = new ScheduleGroupModel(group);
        scheduleGroups.add(groupModel);
      });
    });
  }

  TimeTable.prototype.initHolidays = function() {
    var self = this;
    require([
      'models/holiday',
      'collections/holidays',
      'services/mainService'
    ], function (HolidayModel, holidays, MainService) {
      _.each(MainService.getHoliday(self), function(holiday) {
        var holidayModel = new HolidayModel(holiday);
        holidays.add(holidayModel);
      });
    });
  }

  TimeTable.prototype.getStartDateOfSemester = function() {
    var self = this;
    require([
      'common',
      'services/mainService'
    ], function (common, MainService) {
      common.startDateOfSemester = MainService.getStartDateOfSemester(self);
    });
  }

  TimeTable.prototype.arrangeSchedule = function() {
    var self = this;
    require([
      'models/timeTable',
      'views/windowView',
      'common'
    ], function (TimeTableModel, WindowView, common) {

      common.bizTypeId = self.options.bizTypeId;
      common.semesterId = self.options.semesterId;

      var timeTable = new TimeTableModel(self.options.timeSegmentLayout);

      new WindowView({
        el: self.$el,
        timeTable: timeTable,
        weekdayList: self.options.weekdayList
      });
    });
  }

  TimeTable.prototype.unDraggableTimeTable = function() {
    this.$el.empty();
    var self = this;
    require([
      'models/timeTable',
      'views/timeTableView',
      'collections/holidays',
      'collections/weekdayColumns',
      'collections/warnCards',
      'common'
    ], function (TimeTableModel, TimeTableView, holidays, weekdayColumns, warnCards, common) {

      common.bizTypeId = self.options.bizTypeId;
      common.semesterId = self.options.semesterId;

      if (!common.units.length) {
        common.units = self.options.timeSegmentLayout.units;
        _.each(self.options.timeSegmentLayout.units, function (unit) {
          common.startTimeList.push(unit.startTimeText);
          common.endTimeList.push(unit.endTimeText);
        });
      }

      var timeTable = new TimeTableModel(self.options.timeSegmentLayout);

      var tableView = new TimeTableView({
        timeTable: timeTable,
        weekdayList: self.options.weekdayList,
        parentEl: self.$el,
        draggable: false,
        courseTable: self.options.courseTable
      });
      tableView.render();
    });
  }

  TimeTable.prototype.clearData = function() {
    require([
      'collections/lessons',
      'collections/cards',
      'collections/scheduleGroups',
      'collections/holidays',
      'collections/weekdayColumns',
      'collections/warnCards',
      'common'
    ], function (lessons, cards, scheduleGroups, holidays, weekdayColumns, warnCards, common) {

      common.bizTypeId = null;
      common.semesterId = null;

      lessons.reset();
      cards.reset();
      scheduleGroups.reset();
      holidays.reset();
      weekdayColumns.reset();
      warnCards.reset();
    });
  }

  /**
   * destroy semiAutoTable
   */
  TimeTable.prototype.destroy = function () {

    this.clearData();
    this.$el.removeData('timeTable');
    this.$el.empty();

  }

  // TimeTable PLUGIN DEFINITION
  // =======================

  function Plugin(option) {
    var args = arguments;
    var ret;
    this.each(function () {
      var $this = $(this);
      var data = $this.data('timeTable');

      if (!data) {
        var options = $.extend(true, {}, $.fn.timeTable.defaults, typeof option == 'object' && option);
        $this.data('timeTable', (data = new TimeTable(this, options)))
      }

      if (typeof option == 'string') {
        if (args.length == 1) {
          var _ret = data[option].call(data);
          if (typeof _ret != 'undefined') {
            ret = _ret;
          }
        } else {
          var _ret = data[option].apply(data, Array.prototype.slice.call(args, 1));
          if (typeof _ret != 'undefined') {
            ret = _ret;
          }
        }
      }
    })

    if (typeof ret != 'undefined') {
      return ret;
    }
    return this;

  }

  var old = $.fn.timeTable

  $.fn.timeTable = Plugin
  $.fn.timeTable.Constructor = TimeTable
  $.fn.timeTable.defaults = {

    bizTypeId: null,

    semesterId: null,

    timeSegmentLayout: null,

    lessons: [],

    schedules: [],

    scheduleGroups: [],

    weekdayList: [],

    //只显示课表
    unDraggableTimeTable: false

  };

  // TimeTable NO CONFLICT
  // =================
  $.fn.timeTable.noConflict = function () {

    $.fn.timeTable = old;
    return this;

  }

}(jQuery);
