$(function () {

  $.fn.extend({
    defaults: {
      bizTypeId: null,
      studentId: null,
      turnId: null,
      turnMode: {
        enableVirtualWallet: false
      }
    },
    //向后台获取数据的时间设为10s
    TTL_mills: 10 * 1000,

    bulletinCycle: 30 * 24 * 60 * 60 * 1000,

    //开课单位
    openDepartments: [],
    //学历层次
    courseEducations: [],
    //课程层次
    courseGradations: [],
    //校区
    campus: [],
    //课堂类型
    classTypes: [],
    //课程类型
    courseTypes: [],
    //小节
    courseUnits: [],

    startRefreshStdCount: true,

    predicateResult: [],

    requireConfig: function () {
      require.config({
        baseUrl: window.CONTEXT_PATH + '/static/courseselect',

        paths: {
          text: window.CONTEXT_PATH + '/static/eams-ui/js/text'
        }
      });
    },

    /**
     * 模糊匹配
     * @param $table
     * @param i
     * @param filterVal
     */
    filterColumnWildcard: function ($table, i, filterVal) {
      $table.DataTable().column(i).search(filterVal, true, false).draw();
    },

    /**
     * 精确匹配
     * @param $table
     * @param i
     * @param filterVal
     */
    filterColumn: function ($table, i, filterVal) {
      var regExSearch = '^' + filterVal + '$';
      $table.DataTable().column(i).search(filterVal ? regExSearch : filterVal, true, false).draw();
    },

    filterGlobal: function ($table) {
      $table.DataTable().search($('#global_filter').val(), false, true).draw();
    },

    getCurrentPageWithAddLessons: function (dataTable) {
      var currentPageLessons = [];

      if (dataTable == undefined) {
        $.each($('.dataTable:visible tbody tr:visible'), function () {
          var $btn = $(this).find('td:eq(0) button');
          if (!!$btn.length) {
            currentPageLessons.push($btn.data('id'));
          }
        });
      } else if (dataTable && !!dataTable.length) {
        $.each(dataTable.find('tbody tr'), function () {
          var $btn = $(this).find('td:eq(0) button');
          if (!!$btn.length) {
            currentPageLessons.push($btn.data('id'));
          }
        });
      }

      return currentPageLessons;
    },

    getCurrentPageWithSelectedLessons: function () {
      var currentPageLessons = [];
      var rows = $('#selected-lessons-table tbody tr:visible .drop-course');

      $.each(rows, function () {
        currentPageLessons.push($(this).data('id'));
      });
      return currentPageLessons;
    },

    initConditions: function (lessons) {
      this.courseTypes = this.getDatasByType(lessons, 'courseType');
      this.openDepartments = this.getDepartmentDatas(lessons, 'openDepartment');
      this.campus = this.getDatasByType(lessons, 'campus');
      this.classTypes = this.getDatasByType(lessons, 'classType');
      this.courseUnits = this.getDatasByUnits(lessons, 'unitText');
      this.courseGradations = this.getDatasByGradation(lessons, 'courseGradation');
      this.courseEducations = this.getDatasByCourseGradation(lessons, 'courseGradation');
    },

    getDatasByGradation: function (lessons, type) {
      var dupes = {};
      var singles = [];
      $.each(lessons, function (i, el) {
        if (!el[type]) {
          el[type] = {nameZh: '', nameEn: ''};
          return;
        }
        if (!dupes[el[type].id]) {
          dupes[el[type].id] = true;
          singles.push({value: el[type].id, text: el[type].nameZh, educationId: el[type]['education'] != null ? el[type]['education'].id : ''});
        }
      });
      return singles;
    },

    getDatasByCourseGradation: function (lessons, type) {
      var dupes = {};
      var singles = [];
      $.each(lessons, function (i, el) {
        if (!el[type]) {
          el[type] = {nameZh: '', nameEn: ''};
          return;
        }
        if (!dupes[el[type].id]) {
          dupes[el[type].id] = true;
          if (el[type]['education'] != null && typeof el[type]['education'] != 'undefined') {
            singles.push({value: el[type]['education'].id, text: el[type]['education'].nameZh});
          }
        }
      });
      return singles;
    },

    getDatasByType: function (lessons, type) {
      var dupes = {};
      var singles = [];
      $.each(lessons, function (i, el) {
        if (!el[type]) {
          el[type] = {nameZh: '', nameEn: ''};
          return;
        }
        if (!dupes[el[type].id]) {
          dupes[el[type].id] = true;
          singles.push({value: el[type].id, text: el[type].nameZh});
        }
      });
      return singles;
    },
    getDepartmentDatas: function (lessons, type) {
      var dupes = {};
      var singles = [];
      $.each(lessons, function (i, el) {
        if (!el[type]) {
          el[type] = {nameZh: '', nameEn: ''};
          return;
        }
        if (!dupes[el[type].id]) {
          dupes[el[type].id] = true;
          singles.push(el[type]);
        }
      });
      return singles;
    },

    getDatasByUnits: function (lessons, type) {
      var dupes = {};
      var singles = [];
      $.each(lessons, function (i, el) {
        if (!el[type]) {
          el[type] = {nameZh: '', nameEn: ''};
          return;
        }
        if (!dupes[el[type].text]) {
          dupes[el[type].text] = true;
          // var text = el[type].text.replace(/\n/g, ' ');
          // singles.push({value: text, text: text});
          var texts = el[type].text.split('\n');
          $.each(texts, function () {
            singles.push({value: this.toString(), text: this.toString()});
          });
        }
      });
      return singles;
    },


    getDatasByList: function (items, type) {
      var dupes = {};
      var singles = '';
      $.each(items, function (i, item) {
        if (!item) {
          return;
        }

        if (!dupes[item.id]) {
          dupes[item.id] = true;
          if (singles == '') {
            singles += item.nameZh;
          } else {
            singles += ',' + item.nameZh;
          }
        }
      });
      return singles;
    },
    getDatasByUnitsList: function (items, type) {
      var dupes = {};
      var singles = '';
      $.each(items, function (i, item) {
        if (!item) {
          return;
        }

        if (!dupes[item]) {
          dupes[item] = true;
          /*if (singles == '') {
            singles += item;
          } else {
            singles += ',' + item;
          }*/
          singles += '_' + item + ',';
        }
      });
      return singles;
    },

    /**
     * 获得这个学生在本批次开放选课的所有PlanCourse(参选任务)
     * @param options
     * @returns {{}}
     */
    fetchAddableLessons: function (options) {

      var _self = this;
      var key = "cs-addableLessons-" + options.studentId + "-" + options.turnId;
      var addableLessons = _self.cache.getCache(key);

      if (addableLessons == null) {

        $.ajax({
          url: window.CONTEXT_PATH + options.url.fetchAddableLessons,
          type: 'post',
          data: {turnId: options.turnId, studentId: options.studentId},
          async: false,
          success: function (res) {

            addableLessons = {};
            $.each(res, function () {
              var teachers = this.teachers;
              var teachers_str = "";
              $.each(this.teachers, function (index) {
                if (teachers.length == (index + 1)) {
                  teachers_str += window.LOCALE != 'zh' ? this.nameEn : this.nameZh;
                } else {
                  teachers_str += (window.LOCALE != 'zh' ? this.nameEn : this.nameZh) + ",";
                }
              });
              this['teacherStr'] = teachers_str;
            });
            addableLessons['data'] = res;

            _self.cache.putCache(key, addableLessons, _self.TTL_mills);

          }
        });

      }

      // _self.initConditions(addableLessons.data);

      return addableLessons;
    },

    /**
     * 获得这个学生在本批次开放选课
     * @param options
     * @returns {{}}
     */
    fetchFilteredPlanCourses: function (options) {

      var _self = this;
      var allPlanCourses = _self.fetchPlanCourses(options);
      var storagePlanCourses = {};

      var courseIds = _.map(_self.fetchAddableLessons(options).data, function (lesson) {
        return lesson.course.id;
      });
      var enabledCourses = _.filter(allPlanCourses, function (course) {
        return courseIds.indexOf(course.id) != -1;
      });

      storagePlanCourses['data'] = enabledCourses;

      return storagePlanCourses;

    },

    fetchFailedCourses: function (options) {

      var _self = this;
      var key = "cs-failedCourses-" + options.studentId + "-" + options.turnId;
      var storageFailedCourses = _self.cache.getCache(key);

      if (storageFailedCourses == null) {

        $.ajax({
          url: window.CONTEXT_PATH + options.url.fetchFailedCourses,
          type: 'post',
          async: false,
          data: {
            studentId: options.studentId,
            turnId: options.turnId
          },
          success: function (res) {

            storageFailedCourses = {};
            storageFailedCourses['data'] = res;

            _self.cache.putCache(key, storageFailedCourses, _self.TTL_mills);
          }
        });

      }
      return storageFailedCourses;
    },

    dirtySelectLessons: function (options) {
      var _self = this;
      var key = "cs-selectedLessons-" + options.studentId + "-" + options.turnId;
      _self.cache.makeDirty(key);
    },

    fetchSelectedLessons: function (options) {
      var _self = this;
      var key = "cs-selectedLessons-" + options.studentId + "-" + options.turnId;
      var storageSelectedLessons = _self.cache.getCache(key);

      if (storageSelectedLessons == null || storageSelectedLessons.data.length == 0) {

        $.ajax({
          url: window.CONTEXT_PATH + options.url.fetchSelectedCourses,
          type: 'post',
          async: false,
          data: {
            studentId: options.studentId,
            turnId: options.turnId
          },
          success: function (res) {

            $.each(res, function () {
              var teachers = this.teachers;
              var teachers_str = "";
              $.each(this.teachers, function (index) {
                if (teachers.length == (index + 1)) {
                  teachers_str += window.LOCALE != 'zh' ? this.nameEn : this.nameZh;
                } else {
                  teachers_str += (window.LOCALE != 'zh' ? this.nameEn : this.nameZh) + ",";
                }
              });
              this['teacherStr'] = teachers_str;
            });

            storageSelectedLessons = {};
            storageSelectedLessons['data'] = res;

            _self.cache.putCache(key, storageSelectedLessons, _self.TTL_mills);

          }
        });

      }

      // _self.initConditions(storageSelectedLessons.data);
      return storageSelectedLessons;
    },

    fetchStatus: function (options) {
      var electProfile = {
        semesterCreditUpperLimit: 0,
        semesterAmountUpperLimit: 0,
        semesterAmountActual: 0,
        semesterCreditActual: 0,
        totalCreditActual: 0,
        totalCreditUpperLimit: 0,
        totalAmountActual: 0,
        totalAmountUpperLimit: 0,
        virtualCostTotal: 0,
        virtualCostSpent: 0
      };
      $.ajax({
        url: window.CONTEXT_PATH + options.url.fetchStatus,
        type: 'post',
        data: {
          turnId: options.turnId,
          studentId: options.studentId
        },
        async: false,
        success: function (res) {
          if (res) {
            electProfile = res;
          }
        }
      });

      return electProfile;
    },

    getTimeSegments: function (courseUnits) {
      var timeSegments = [];
      var segStartTime = parseInt(courseUnits[0].startTime / 100) * 100;
      var segEndTime;
      var endTime = courseUnits[courseUnits.length - 1].endTime;
      if ((endTime / 100) > parseInt(endTime / 100)) {
        segEndTime = (parseInt(endTime / 100) + 1) * 100;
      } else {
        segEndTime = (endTime / 100) * 100;
      }
      for (var start = segStartTime; start < segEndTime; start = start + 100) {
        timeSegments.push({
          startTime: start,
          endTime: start + 100
        });
      }

      return timeSegments;
    },

    /**
     * 释放LocalStorage空间
     * @param options
     */
    releaseLocalStorage: function (options) {
      if (window.localStorage && window.localStorage.getItem) {
        // 清理掉别人的localStorage缓存
        var removeKeys = [];
        for (var key in window.localStorage) {
          if (key.indexOf("-table", key.length - "-table".length) !== -1) {
            continue;
          }
          if (key === "current_semester") {
            continue;
          }

          if (key.indexOf("cs-") != 0) {
            // 不是选课缓存
            removeKeys.push(key);
            continue;
          }
          if (key.indexOf("-" + options.studentId + "-" + options.turnId) == -1) {
            // 不是当前学生,当前批次的选课缓存
            removeKeys.push(key);
            continue;
          }
        }
        _.each(removeKeys, function (key) {
          window.localStorage.removeItem(key);
        });
      }

    },

    initTabs: function (options) {

      this.releaseLocalStorage(options);

      this.requireConfig();
      var _self = this;

      var options = $.extend({}, this.defaults, options);


      function setCookie(cname,cvalue,exdays){
        var d = new Date();
        d.setTime(d.getTime()+exdays);
        var expires = "expires="+d.toGMTString();
        document.cookie = cname+"="+cvalue+"; "+expires;
      }

      function getCookie(cname){
        var name = cname + "=";
        var ca = document.cookie.split(';');
        for(var i=0; i<ca.length; i++) {
          var c = ca[i].trim();
          if (c.indexOf(name)==0) {
            return c.substring(name.length,c.length);
          }
        }
        return "";
      }

      //弹出提示框(推荐课程页面出现滚动条，则页面会出现抖动)
      var courseSelectLocalStorage = 'course-select-' + options.turnId;
      var getCourseSelect = getCookie(courseSelectLocalStorage);
      var isShow = false;
      if (getCourseSelect == '' && options.bulletin != null) {
        var bulletin = options.bulletin.replace(/\n/g, '<br/>');
        $(".alter-modal").find('.course-select-bulletin').html(bulletin);
        $(".alter-modal").modal('show');

        $(".no-longer-prompt").click(function () {
          setCookie(courseSelectLocalStorage, true, _self.bulletinCycle);
        });

        $(".alter-modal").on('hidden.bs.modal', function (e) {
          $("#my-tab-content").find("#all-lessons").addClass("active");
        });
      } else {
        isShow = true;
      }

      require([
        'text!template/tabs-by-graduate.html',
        'text!template/selected-lessons-by-graduate.html',
        'js/cache'
      ], function (tabsTemp, selectedLessonsTmpl, cache) {

        _self.cache = cache;

        var tabs_template = _.template(tabsTemp);
        _self.append(tabs_template());

        //状态
        _self.renderStatus(options);

        //渲染全部课程
        _self.renderAllLessonsTable(options, isShow);

        //渲染已选课程
        _self.renderSelectedLessonTable(options, selectedLessonsTmpl);

      });
    },

    renderStatus: function (options) {
      var _self = this;

      //所有学期选课结果 @Author: Jun.Wang @Date: 2026-06-14
      $(".all-course-select").on('click', function () {
        window.open(window.CONTEXT_PATH + '/for-std/course-select/' + options.studentId + '/turn/'+ options.turnId + '/all-course-takes', 'allCourseTakes');
      });

      //打印选课单 @Author: Jun.Wang @Date: 2026-06-14
      $(".print-course-select").on('click', function () {
        window.open(window.CONTEXT_PATH + '/for-std/course-select/' + options.studentId + '/turn/'+ options.turnId + '/semester-course-takes', 'semesterCourseTakes');
      });

      //我的课表
      $(".my-course-table").on('click', function () {

        var weekdayList = [1, 2, 3, 4, 5, 6, 7];

        var lessons = [];
        var schedules = [];
        var scheduleGroups = [];

        var courseUnits = [];
        $.ajax({
          url: window.CONTEXT_PATH + "/ws/schedule-table/timetable-layout",
          type: 'post',
          contentType: 'application/json',
          async: false,
          data: JSON.stringify({
            timeTableLayoutId: 1
          }),
          success: function (res) {
            courseUnits = res.result.courseUnitList;
          }
        });

        var timeSegmentLayout = {
          timeSegments: _self.getTimeSegments(courseUnits),
          units: courseUnits
        };

        var selected_lessons = _self.fetchSelectedLessons(options).data;
        if (selected_lessons.length > 0) {
          var data = {};
          data['lessonIds'] = _.map(selected_lessons, function (lesson) {
            return lesson.id;
          });
          data['studentId'] = options.studentId;

          $.ajax({
            url: window.CONTEXT_PATH + "/ws/schedule-table/datum",
            type: 'post',
            contentType: 'application/json',
            async: false,
            data: JSON.stringify(data),
            success: function (res) {
              lessons = res.result.lessonList;
              schedules = res.result.scheduleList;
              scheduleGroups = res.result.scheduleGroupList;
            }
          });
        }

        $('.course-table-modal').modal('show');
        $(".course-table-modal .course-table-block").timeTable({
          semesterId: options.semesterId,

          timeSegmentLayout: timeSegmentLayout,

          lessons: lessons,

          schedules: schedules,

          scheduleGroups: scheduleGroups,

          weekdayList: weekdayList,

          unDraggableTimeTable: true
        });
      });

      $('.course-table-modal').on('hidden.bs.modal', function (e) {
        $(".course-table-modal .course-table-block").timeTable('destroy');
      });

    },

    renderStatusBySuitable: function (lessons, $target) {

      if (!!lessons.length) {
        $target.find("button").removeClass("course-select").addClass("info-course").text("查看");
        $target.find("button").removeClass("btn-primary").addClass("btn-default");
      }
    },

    judgeIncludeLesson: function (data, options, id, type) {

      var selectedLesson = null;
      $.each(data, function () {
        if ('course' == type) {
          if (this.course && id == this.course.id) {
            selectedLesson = this;
          }
        } else if ('lesson' == type) {
          if (id == this.id) {
            selectedLesson = this;
          }
        }
      });
      return selectedLesson;
    },

    bindPullUpAndOpenOpe: function () {

      //学业进展情况的展开与收起
      $("button.pull-up").unbind('click').click(function () {

        var is_pull_up = $(this).attr("is-pull-up");

        if (is_pull_up == 0) {
          $(this).attr("is-pull-up", 1);
          $(this).closest(".program-list-box").find(".course-type-div").hide();
          $(this).html("<i class='fa fa-angle-down'/> 学业进展情况");

        } else {
          $(this).attr("is-pull-up", 0);
          $(this).closest(".program-list-box").find(".course-type-div").show();
          $(this).html("<i class='fa fa-angle-up'/> 学业进展情况");
        }

      });

      //课程模块的展开与收起
      $(".course-module").unbind('click').click(function () {

        var courseModuleId = $(this).closest("tr").data("id");
        var $currentTable = $(this).closest("table");

        if ($(this).find("i").hasClass("fa-angle-up")) {
          $(this).find("i").removeClass("fa-angle-up").addClass("fa-angle-down");

          $.each($currentTable.find("tbody>tr"), function () {

            if ($(this).data("parent") == courseModuleId) {
              $(this).addClass('hide');
            }
          });
        } else {
          $(this).find("i").removeClass("fa-angle-down").addClass("fa-angle-up");

          $.each($currentTable.find("tbody>tr"), function () {

            if ($(this).data("parent") == courseModuleId) {
              $(this).removeClass('hide');
            }
          });
        }

      });
    },

    renderAllLessonsTable: function (options, isShow) {
      this.requireConfig();
      var _self = this;

      var options = $.extend({}, this.defaults, options);
      var $table;
      require([
        'text!template/all-lessons-by-graduate.html'
      ], function (allLessonTemp) {
        //渲染全部课程
        var all_template = _.template(allLessonTemp);
        _self.find("#my-tab-content").append(all_template());

        $table = _self.find("#all-lessons-table");

        //停止别的页面定时查询人数
        _self.stopRefreshStdCountPeriodically();

        var data = _self.fetchAddableLessons(options).data;

        //渲染查询条件
        _self.initConditions(data);
        _self.initAllLessonConditions(options, $table);

        //全部课程的查询条件的伸展收缩
        _self.allLessonConditionPullOpe();

        if (isShow) {
          _self.find("#my-tab-content").find("#all-lessons").addClass("active");
        }

        var all_lessons_table = null;

        var selected_lessons = _self.fetchSelectedLessons(options).data;

        if (all_lessons_table) {
          all_lessons_table.destroy();
          $table.find('tbody').remove();
        }

        all_lessons_table = $table.DataTable({
          data: data,
          "autoWidth": false,
          "columns": [
            {
              "data": "id",
              /* 操作列：选课/退课按钮 @Author: Jun.Wang @Date: 2026-06-13 */
              "render": function (field, row) {
                var btn = '';
                var selectedLesson = _self.judgeIncludeLesson(selected_lessons, options, field.data, 'lesson');
                if (selectedLesson) {
                  btn = '<button data-id="' + field.data + '" class="btn btn-primary drop-course" value="退课">退课</button>'; /* 退课按钮默认蓝色，悬停变红 @Author: Jun.Wang @Date: 2026-06-14 */
                } else {
                  btn = '<button data-id="' + field.data + '" class="btn btn-primary course-select" value="选课">选课</button>'; /* 选课按钮保持蓝色 @Author: Jun.Wang @Date: 2026-06-14 */
                }
                btn += '<input type="hidden" value="'+ row.data.enablePreSelect +'" class="course-enablePreSelect"/>';
                return btn;
              }
            },
            {
              "data": "id",
              /* 选课状态：已选中(绿) / 待抽签(橙) / 空 @Author: Jun.Wang @Date: 2026-06-13 */
              "render": function (field) {
                var label = '';
                var selectedLesson = _self.judgeIncludeLesson(selected_lessons, options, field.data, 'lesson');
                if (selectedLesson) {
                  if (!selectedLesson.pinned) {
                    label = '<label class="control-label pinned-label">待抽签</label>';
                  } else {
                    label = '<label class="control-label drop-label">已选中</label>';
                  }
                } else {
                  label = '<label class="control-label select-label1"></label>';
                }
                return label;
              }
            },
            /* 参选状态：隐藏原列 @Author: Jun.Wang @Date: 2026-06-13 */
            {"data": window.LOCALE != 'zh' ? "selectionState.nameEn" : "selectionState.nameZh", "visible": false},
            {
              "data": "code",
              /* 课堂号：隐藏原列，已合并到课程信息下方 @Author: Jun.Wang @Date: 2026-06-13 */
              "visible": false
            },
            {
              "data": window.LOCALE != 'zh' ? "course.nameEn" : "course.nameZh",
              /* 课程信息：放大加粗+info图标+悬停提示+点击弹信息，下方展示课堂号/学分/授课语言/课程性质/学历层次/校区 @Author: Jun.Wang @Date: 2026-06-14 */
              "render": function (field, row) {
                var lessonCode = row.data.code || '';
                var lessonId = row.data.id != null ? row.data.id : null;
                var credits = row.data.course.credits || '';
                var teachLang = (row.data.teachLang) ? row.data.teachLang.nameZh : ''; /* 授课语言 @Author: Jun.Wang @Date: 2026-06-14 */
                var selectionType = (row.data.selectionType) ? row.data.selectionType.nameZh : '';
                var education = (row.data.courseGradation && row.data.courseGradation.education) ? row.data.courseGradation.education.nameZh : '';
                var campus = _self.getDatasByList([row.data.campus], 'campus');

                // 构建课程名称下方的标签行：课堂号 + | 分隔的其他信息（授课语言在学分与学历层次之间）@Author: Jun.Wang @Date: 2026-06-14
                var subInfo = '';
                var parts = [];
                if (lessonCode) parts.push(lessonCode);
                if (credits) parts.push(credits + '学分');
                if (teachLang) parts.push(teachLang); /* 授课语言 @Author: Jun.Wang @Date: 2026-06-14 */
                if (selectionType) parts.push(selectionType);
                if (education) parts.push(education);
                if (campus) parts.push(campus);
                var infoText = parts.join(' \u00A0|\u00A0 ');

                if (infoText) {
                  subInfo = '<div class="course-sub-info"> ' + infoText + ' </div>';
                }

                // 课程名称可点击，弹出课堂信息，info图标提示可点击 @Author: Jun.Wang @Date: 2026-06-13
                return '<div class="course-name-main click-course-info" data-id="' + lessonId + '" title="点击查看课堂详情" style="cursor:pointer;">'
                  + field.data
                  + '<i class="fa fa-info-circle course-name-info-icon"></i>'
                  + '</div>' + subInfo;
              }
            },
            /* 学分：隐藏原列，已合并到课程信息下方 @Author: Jun.Wang @Date: 2026-06-13 */
            {"data": "course.credits", "visible": false},
            {"data": "openDepartment.nameZh"},
            /* 课程性质：隐藏原列，已合并到课程信息下方 @Author: Jun.Wang @Date: 2026-06-13 */
            {"data": "selectionType.nameZh", "visible": false},
            /* 学历层次：隐藏原列，已合并到课程信息下方 @Author: Jun.Wang @Date: 2026-06-13 */
            {"data": "courseGradation.education.nameZh", "visible": false},
            {"data": "courseGradation.nameZh"},
            {"data": "teacherStr",
              /* 授课教师：最多3行，超出悬停展开显示，支持点击教师链接弹出介绍 @Author: Jun.Wang @Date: 2026-06-13 */
              "render": function (field, row) {
                var str = '<span class="teacher-name teacher-cell">'
                var teacherStr = '';
                $.each(row.data.teachers,function(i,item){
                  teacherStr += '<a class="click-teacher-info" lessonId="'+ row.data.id +'" data-id="' + item.id + '" title="点击查看教师详情">' + item.nameZh + '</a>,';
                })
                str += teacherStr.substring(0,teacherStr.length -1);
                str += '</span>';
                return str;
              }
            },
            {
              "data": "weekDayPlaceText.textZh",
              /* 上课地点时间：按分号分隔换行，最多3行显示，超出省略号，鼠标悬停按分号换行显示完整内容 @Author: Jun.Wang @Date: 2026-06-13 */
              "render": function (field, row) {
                var fullText = field.data || '';
                var displayHtml = fullText.replace(/;/g, ';<br/>');
                var titleText = fullText.replace(/;/g, ';\n');
                return '<span class="location-time-cell" title="' + titleText.replace(/"/g, '&quot;') + '">' + displayHtml + '</span>';
              }
            },
            /* 上课节次：隐藏原列 @Author: Jun.Wang @Date: 2026-06-13 */
            {
              "data": "id",
              "visible": false,
              "render": function (field, row) {
                return _self.getDatasByUnitsList(row.data.units, 'units');
              }
            },
            {
              "data": "weekText.textZh",
              /* 起止周：按分号分隔换行，最多显示3行，超出省略号，鼠标悬停按分号换行显示完整内容 @Author: Jun.Wang @Date: 2026-06-13 */
              "render": function (field, row) {
                var fullText = field.data || '';
                var displayHtml = fullText.replace(/;/g, ';<br/>');
                var titleText = fullText.replace(/;/g, ';\n');
                return '<span class="week-range-cell" title="' + titleText.replace(/"/g, '&quot;') + '">' + displayHtml + '</span>';
              }
            },
            /* 校区：隐藏原列，已合并到课程信息下方 @Author: Jun.Wang @Date: 2026-06-13 */
            {
              "data": "id",
              "visible": false,
              "render": function (field, row) {
                return _self.getDatasByList([row.data.campus], 'campus');
              }
            },
            /* 授课语言：已合并到课程信息下方，隐藏原列 @Author: Jun.Wang @Date: 2026-06-14 */
            {"data": "teachLang.nameZh", "visible": false},
            /* 开结课时间：不需要显示，隐藏 @Author: Jun.Wang @Date: 2026-06-14 */
            {"data": "startAndEndDateStr", "visible": false},
            {
              "data": "limitCount",
              "render": function(field) {
                if (options.turnMode.showCount) {
                  return '<div style="width: 80%">' +
                      '<div class="progress-text text-center"><span class="std-count">0</span>/<span class="limit-count">' + field.data + '</span></div>' +
                      '<div class="progress" style="height: 5px;">' +
                      '<div class="progress-bar progress-bar-primary std-count-progress" role="progressbar">' +
                      '</div></div>' +
                      '</div>';
                } else {
                  return '<div class="not-show-count" limit-count="' + field.data + '"></div>';
                }
              }
            },
            /* 课堂说明：隐藏原列，已合并到课程信息下方 @Author: Jun.Wang @Date: 2026-06-13 */
            {"data": "remark", "visible": false}
          ],
          "aoColumnDefs": [{"orderable": false, "targets": [0, 1]}],
          "lengthMenu": [[20, 50, 100, 200, 500, -1], [20, 50, 100, 200, 500, 1000]],
          "pagingType": "full",
          /* 去掉上方分页，只保留底部翻页 @Author: Jun.Wang @Date: 2026-06-13 */
          "dom": 'rt<"row"<"col-md-12"<"pull-right"<"pull-left"i><"pageInfoStyle1"p>>>>',
          "language": {
            "info": "<a class='btn btn-default disabled'>_START_-_END_ of _TOTAL_</a>",
            "infoEmpty": "<a class='btn btn-default disabled'>_START_-_END_ of _TOTAL_</a>",
            "sInfoFiltered": "",
            "sZeroRecords": "无数据",
            "search": "<a><i class='fa fa-search'></i></a> _INPUT_ <br/>",
            "lengthMenu": "_MENU_",
            "paginate": {
              "next": '<i class="fa fa-angle-right title-icon"></i>',
              "previous": '<i class="fa fa-angle-left title-icon"></i>',
              "first": '<i class="fa fa-angle-double-left title-icon"></i>',
              "last": '<i class="fa fa-angle-double-right title-icon"></i>'
            }
          },
          "drawCallback": function () {
            $table.find("thead>tr>th").eq(0).addClass("sorting_disabled").removeClass("sorting_asc").removeClass("sorting_desc");
            $table.find("thead>tr>th").eq(1).addClass("sorting_disabled").removeClass("sorting_asc").removeClass("sorting_desc");

            _self.refreshStdCount(options, $table);
            _self.courseSelect($table, options);
            _self.addPredicate(options, $table, "lesson");
            _self.addDropLessonFunc(options, $table, 'out');
            $('[data-toggle="tooltip"]').tooltip({container: 'body'});

            $table.find('.click-course-info').unbind('click').click(function () {
              _self.showCourseInfo(options, $(this).data('id'));
            });

            $table.find('.click-teacher-info').unbind('click').click(function () {
              _self.showTeacherInfo(options, $(this).attr('lessonId') ,$(this).data('id'));
            });
          }
        });

        _self.startRefreshStdCountPeriodically(options, $table);
        /* 列可见性已在columns定义中通过visible:false控制，不再需要手动设置 @Author: Jun.Wang @Date: 2026-06-13 */

        $("a.all-lessons").on('shown.bs.tab', function () {

          var selected_lessons = _self.fetchSelectedLessons(options).data;

          //处理全部课程tab上已选课程
          $.each($table.find("tbody tr"), function () {

            var $btn = $(this).find('td:eq(0) button');
            var $label = $(this).find('td:eq(1) label');
            var selectedLesson = _self.judgeIncludeLesson(selected_lessons, options, $btn.data('id'), 'lesson');

            if (selectedLesson) {
              $btn.removeClass("course-select").addClass("drop-course").text("退课");
              /* 已选中：按钮保持蓝色，悬停时CSS自动变红 @Author: Jun.Wang @Date: 2026-06-14 */
              if (!selectedLesson.pinned) {
                $label.addClass('pinned-label').removeClass('select-label1').removeClass('drop-label').text("待抽签");
              } else {
                $label.addClass('drop-label').removeClass('select-label1').removeClass('pinned-label').text("已选中");
              }
              //重新绑定选课事件
              _self.addDropLessonFunc(options, $table, 'out');
            } else {
              $btn.removeClass("drop-course").addClass("course-select").text("选课");
              /* 未选中：按钮保持蓝色，悬停时CSS自动变绿 @Author: Jun.Wang @Date: 2026-06-14 */
              $label.addClass('select-label1').removeClass('drop-label').removeClass('pinned-label').text("");
              //重新绑定选课事件
              _self.courseSelect($table, options);
            }
          });
        });
      });
    },
    /* 点击选课说明加载选课说明数据（填充 course-info-modal 的"选课说明"标签页） */
    showRemark: function (options, lessonId, skipLessonInfo) {
      if (lessonId == null) {
        return;
      }
      var $modal = $(".course-info-modal");

      if (!skipLessonInfo) {
      $.ajax({
        url: window.CONTEXT_PATH + options.url.lessonInfo,
        type: 'post',
        async: false,
        data: {"lessonId": lessonId},
        success: function (res) {
          $modal.find('.modal-header').find(".modal-title-code").text(res.code);
          $modal.find('.modal-header').find(".modal-title-nameZh").text(res.nameZh);
          $modal.find('.modal-header').find(".modal-title-nameEn").text(res.nameEn);
        }
      });
      }

      $.ajax({
        url: window.CONTEXT_PATH + options.url.remarkDetail,
        type: 'post',
        async: false,
        data: {"lessonId": lessonId, turnId: options.turnId},
        success: function (res) {

          $modal.find('.modal-body').find(".selectRemark").text(res.lesson.remark ? res.lesson.remark : "");
          $modal.find('.modal-body').find(".individualDateTime").empty();
          $modal.find('.modal-body').find(".individualDateTime").append(res.individualDateTime ? res.individualDateTime : "");

          var parentCodeList = {};
          _.each(res.selectionGroupsJson, function (selectionGroup) {
            var parentCode = selectionGroup.parentCode;
            if (!parentCodeList[parentCode]) {
              parentCodeList[parentCode] = {};
            }
            parentCodeList[parentCode] = selectionGroup;
          });
          var firstCodeList = {};
          _.each(res.firstGroupsJson, function (firstGroup) {
            var parentCode = firstGroup.parentCode;
            if (!firstCodeList[parentCode]) {
              firstCodeList[parentCode] = {};
            }

            firstCodeList[parentCode] = firstGroup;
          });

          var selectStrItems = [];
          var noStrItems = [];
          var firstStrItems = [];

          for (var i in parentCodeList) {
            var selectionGroup = parentCodeList[i];
            var firstGroup = firstCodeList[i];

            var trainType = '';
            var trainType_no = '';
            var trainType_first = '';

            var gender = '';
            var gender_no = '';
            var gender_first = '';

            var grade = '';
            var grade_no = '';
            var grade_first = '';

            var stdType = '';
            var stdType_no = '';
            var stdType_first = '';

            var mngtDepartment = '';
            var mngtDepartment_no = '';
            var mngtDepartment_first = '';

            var department = '';
            var department_no = '';
            var department_first = '';

            var major = '';
            var major_no = '';
            var major_first = '';

            var majorDirection = '';
            var majorDirection_no = '';
            var majorDirection_first = '';

            var adminclass = '';
            var adminclass_no = '';
            var adminclass_first = '';

            var selectItems = [];
            var noItems = [];
            var firstItems = [];

            // 培养类型
            var trainTypeItems = []
            _.each(selectionGroup.trainTypeList, function (o) {
              trainTypeItems.push(o.nameZh);
            });
            var first_trainTypeItems = []
            if (firstGroup) {
              _.each(firstGroup.trainTypeList, function (o) {
                first_trainTypeItems.push(o.nameZh);
              });
            }
            if (selectionGroup.trainTypeMode == "IN") {
              trainType = trainTypeItems.join(",");
              if (trainType) {
                selectItems.push("培养类型：" + trainType);
              }
            } else {
              trainType_no = trainTypeItems.join(",");
              if (trainType_no) {
                noItems.push("培养类型：" + trainType_no);
              }
            }
            trainType_first = first_trainTypeItems.join(",");
            if (trainType_first) {
              firstItems.push("培养类型：" + trainType_first);
            }

            // 性别
            var genderItems = []
            _.each(selectionGroup.genderList, function (o) {
              genderItems.push(o.nameZh);
            });
            var first_genderItems = []
            if (firstGroup) {
              _.each(firstGroup.genderList, function (o) {
                first_genderItems.push(o.nameZh);
              });
            }
            if (selectionGroup.genderMode == "IN") {
              gender = genderItems.join(",");
              if (gender) {
                selectItems.push("性别：" + gender);
              }
            } else {
              gender_no = genderItems.join(",");
              if (gender_no) {
                noItems.push("性别：" + gender_no);
              }
            }
            gender_first = first_genderItems.join(",");
            if (gender_first) {
              firstItems.push("性别：" + gender_first);
            }

            // 年级
            var gradeItems = []
            _.each(selectionGroup.gradeList, function (o) {
              gradeItems.push(o);
            });
            var first_gradeItems = [];
            if (firstGroup) {
              _.each(firstGroup.gradeList, function (o) {
                first_gradeItems.push(o);
              });
            }
            if (selectionGroup.gradeMode == "IN") {
              grade = gradeItems.join(",");
              if (grade) {
                selectItems.push("年级：" + grade);
              }
            } else {
              grade_no = gradeItems.join(",");
              if (grade_no) {
                noItems.push("年级：" + grade_no);
              }
            }
            grade_first = first_gradeItems.join(",");
            if (grade_first) {
              firstItems.push("年级：" + grade_first);
            }

            // 学生类别
            var stdTypeItems = []
            _.each(selectionGroup.stdTypeList, function (o) {
              stdTypeItems.push(o.nameZh);
            });
            var first_stdTypeItems = []
            if (firstGroup) {
              _.each(firstGroup.stdTypeList, function (o) {
                first_stdTypeItems.push(o.nameZh);
              });
            }
            if (selectionGroup.stdTypeMode == "IN") {
              stdType = stdTypeItems.join(",");
              if (stdType) {
                selectItems.push("学生类别：" + stdType);
              }
            } else {
              stdType_no = stdTypeItems.join(",");
              if (stdType_no) {
                noItems.push("学生类别：" + stdType_no);
              }
            }
            stdType_first = first_stdTypeItems.join(",");
            if (stdType_first) {
              firstItems.push("学生类别：" + stdType_first);
            }

            // 管理院系
            var mngtDepartmentItems = []
            _.each(selectionGroup.mngtDepartmentList, function (o) {
              mngtDepartmentItems.push(o.nameZh);
            });
            var first_mngtDepartmentItems = []
            if (firstGroup) {
              _.each(firstGroup.mngtDepartmentList, function (o) {
                first_mngtDepartmentItems.push(o.nameZh);
              });
            }
            if (selectionGroup.mngtDepartmentMode == "IN") {
              mngtDepartment = mngtDepartmentItems.join(",");
              if (mngtDepartment) {
                selectItems.push("管理院系：" + mngtDepartment);
              }
            } else {
              mngtDepartment_no = mngtDepartmentItems.join(",");
              if (mngtDepartment_no) {
                noItems.push("管理院系：" + mngtDepartment_no);
              }
            }
            mngtDepartment_first = first_mngtDepartmentItems.join(",");
            if (mngtDepartment_first) {
              firstItems.push("管理院系：" + mngtDepartment_first);
            }

            // 专业院系
            var departmentItems = []
            _.each(selectionGroup.departmentList, function (o) {
              departmentItems.push(o.nameZh);
            });
            var first_departmentItems = []
            if (firstGroup) {
              _.each(firstGroup.departmentList, function (o) {
                first_departmentItems.push(o.nameZh);
              });
            }
            if (selectionGroup.departmentMode == "IN") {
              department = departmentItems.join(",");
              if (department) {
                selectItems.push("专业院系：" + department);
              }
            } else {
              department_no = departmentItems.join(",");
              if (department_no) {
                noItems.push("专业院系：" + department_no);
              }
            }
            department_first = first_departmentItems.join(",");
            if (department_first) {
              firstItems.push("专业院系：" + department_first);
            }

            // 专业
            var majorItems = []
            _.each(selectionGroup.majorList, function (o) {
              majorItems.push(o.nameZh);
            });
            var first_majorItems = []
            if (firstGroup) {
              _.each(firstGroup.majorList, function (o) {
                first_majorItems.push(o.nameZh);
              });
            }
            if (selectionGroup.majorMode == "IN") {
              major = majorItems.join(",");
              if (major) {
                selectItems.push("专业：" + major);
              }
            } else {
              major_no = majorItems.join(",");
              if (major_no) {
                noItems.push("专业：" + major_no);
              }
            }
            major_first = first_majorItems.join(",");
            if (major_first) {
              firstItems.push("专业：" + major_first);
            }

            // 专业方向
            var majorDirectionItems = []
            _.each(selectionGroup.majorDirectionList, function (o) {
              majorDirectionItems.push(o.nameZh);
            });
            var first_majorDirectionItems = []
            if (firstGroup) {
              _.each(firstGroup.majorDirectionList, function (o) {
                first_majorDirectionItems.push(o.nameZh);
              });
            }
            if (selectionGroup.majorDirectionMode == "IN") {
              majorDirection = majorDirectionItems.join(",");
              if (majorDirection) {
                selectItems.push("专业方向：" + majorDirection);
              }
            } else {
              majorDirection_no = majorDirectionItems.join(",");
              if (majorDirection_no) {
                noItems.push("专业方向：" + majorDirection_no);
              }
            }
            majorDirection_first = first_majorDirectionItems.join(",");
            if (majorDirection_first) {
              firstItems.push("专业方向：" + majorDirection_first);
            }

            // 专业班级
            var adminclassItems = []
            _.each(selectionGroup.adminclassList, function (o) {
              adminclassItems.push(o.nameZh);
            });
            var first_adminclassItems = []
            if (firstGroup) {
              _.each(firstGroup.adminclassList, function (o) {
                first_adminclassItems.push(o.nameZh);
              });
            }
            if (selectionGroup.adminclassMode == "IN") {
              adminclass = adminclassItems.join(",");
              if (adminclass) {
                selectItems.push("专业班级：" + adminclass);
              }
            } else {
              adminclass_no = adminclassItems.join(",");
              if (adminclass_no) {
                noItems.push("专业班级：" + adminclass_no);
              }
            }
            adminclass_first = first_adminclassItems.join(",");
            if (adminclass_first) {
              firstItems.push("专业班级：" + adminclass_first);
            }

            //单个条件文本框内容
            var selectStr = '';
            var noStr = '';
            var firstStr = '';

            selectStr = selectItems.join(" <span style='color: red'> |并且| </span>");
            noStr = noItems.join(" <span style='color: red'> |并且| </span>");
            firstStr = firstItems.join(" <span style='color: red'> |并且| </span>");

            if (selectStr) {
              selectStrItems.push(selectStr);
            }
            if (noStr) {
              noStrItems.push(noStr);
            }
            if (firstStr) {
              firstStrItems.push(firstStr);
            }
          }

          var canSelect = '';
          var noSelect = '';
          var firstSelect = '';

          canSelect = selectStrItems.join("<br/><span style='color: red'> 或 </span><br/>");
          noSelect = noStrItems.join("<br/><span style='color: red'> 或 </span><br/>");
          firstSelect = firstStrItems.join("<br/><span style='color: red'> 或 </span><br/>");

          $modal.find('.modal-body').find(".canSelect").html(canSelect);
          $modal.find('.modal-body').find(".noSelect").html(noSelect);
          $modal.find('.modal-body').find(".firstSelect").html(firstSelect);

          if (!skipLessonInfo) {
            $modal.modal('show');
          }
        }
      });

      $modal.unbind('hidden.bs.modal').on('hidden.bs.modal', function (e) {
        if ($('.modal.fade.in').length > 0) {
          $("body").addClass('modal-open');
        }
      });
    },
    showCourseInfo: function (options, lessonId) {
      if (lessonId == null) {
        return;
      }
      var _self = this;
      var $modal = $(".course-info-modal");

      // 强制重置tab到"课堂信息"（tab('show')在modal未显示时可能不生效，导致停在旧tab上）
      $modal.find('.course-detail-tabs li').removeClass('active');
      $modal.find('.course-detail-tabs a[href="#course-info-tab"]').parent().addClass('active');
      $modal.find('.course-detail-tabs').next('.tab-content').find('.tab-pane').removeClass('active');
      $modal.find('#course-info-tab').addClass('active');
      $modal.removeData('remarkLoadedLessonId');

      $.ajax({
        url: window.CONTEXT_PATH + options.url.lessonInfo,
        type: 'post',
        async: false,
        data: {"lessonId": lessonId},
        success: function (res) {
          $modal.find('.modal-header').find(".modal-title-code").text(res.code);
          $modal.find('.modal-header').find(".modal-title-nameZh").text(res.nameZh);
          $modal.find('.modal-header').find(".modal-title-nameEn").text(res.nameEn);

          $modal.find('.modal-body').find('.openDepartment').text(res.openDepartment ? res.openDepartment.nameZh : '');

          var period = res.requiredPeriodInfo.total != null ? res.requiredPeriodInfo.total : '';
          var subPeroid = [];
          subPeroid.push(res.requiredPeriodInfo.theory != null ? res.requiredPeriodInfo.theory : 0);
          subPeroid.push(res.requiredPeriodInfo.experiment != null ? res.requiredPeriodInfo.experiment : 0);
          subPeroid.push(res.requiredPeriodInfo.practice != null ? res.requiredPeriodInfo.practice : 0);
          if (subPeroid.length > 0) {
            period += "(" + subPeroid.join("/") + ")";
          }
          $modal.find('.modal-body').find('.total').text(period);

          $modal.find('.modal-body').find('.season').text(res.season ? res.season.nameZh : '');
          $modal.find('.modal-body').find('.scoreMarkStyle').text(res.scoreMarkStyle ? res.scoreMarkStyle.nameZh : '');
          $modal.find('.modal-body').find('.teachingObject').text(res.teachingObject ? res.teachingObject.nameZh : '');
          $modal.find('.modal-body').find('.preCourse').text(res.lessonPreCourseText ? res.lessonPreCourseText : '');
          $modal.find('.modal-body').find('.examMode').text(res.examMode ? res.examMode.nameZh : '');
          $modal.find('.modal-body').find('.teachLang').text(res.teachLang ? res.teachLang.nameZh : '');

          /*$modal.find('.modal-body').find('#textbook-table tbody').empty();
          if (res.needTextbook != null) {
            if (res.needTextbook) {
              if (!$modal.find('.modal-body').find('.dontNeedTextbook').hasClass("hide")) {
                $modal.find('.modal-body').find('.dontNeedTextbook').addClass("hide")
              }
              if ($modal.find('.modal-body').find('#textbook-table').hasClass("hide")) {
                $modal.find('.modal-body').find('#textbook-table').removeClass("hide")
              }
              if (res.publish) {
                if ($modal.find('.modal-body').find('#textbook-table .textbook-title').hasClass('hide')) {
                  $modal.find('.modal-body').find('#textbook-table .textbook-title').removeClass('hide');
                }
                if (!$modal.find('.modal-body').find('#textbook-table .handout-title').hasClass('hide')) {
                  $modal.find('.modal-body').find('#textbook-table .handout-title').addClass('hide');
                }

                $.each(res.textbooks, function (index, item) {
                  $modal.find('.modal-body').find('#textbook-table tbody').append(`
                  <tr>
                   <td>${item.nameZh}</td>
                   <td>${item.publish ? '教材' : '讲义'}</td>
                   <td>${item.author}</td>
                   <td>${item.isbn}</td>
                   <td>${item.publishingHouse ? item.publishingHouse : ''}</td>
                   <td>${item.edition ? item.edition : ''}</td>
                   <td>${item.dates ? item.dates : ''}</td>
                  </tr>
                `);
                });
              } else {
                if ($modal.find('.modal-body').find('#textbook-table .handout-title').hasClass('hide')) {
                  $modal.find('.modal-body').find('#textbook-table .handout-title').removeClass('hide');
                }
                if (!$modal.find('.modal-body').find('#textbook-table .textbook-title').hasClass('hide')) {
                  $modal.find('.modal-body').find('#textbook-table .textbook-title').addClass('hide');
                }

                $.each(res.textbooks, function (index, item) {
                  $modal.find('.modal-body').find('#textbook-table tbody').append(`
                  <tr>
                   <td>${item.nameZh}</td>
                   <td>${item.publish ? '教材' : '讲义'}</td>
                   <td>${item.author}</td>
                   <td>${item.semester ? item.semester.nameZh : ''}</td>
                  </tr>
                `);
                });
              }
            } else {
              if (!$modal.find('.modal-body').find('#textbook-table').hasClass("hide")) {
                $modal.find('.modal-body').find('#textbook-table').addClass("hide")
              }
              if ($modal.find('.modal-body').find('.dontNeedTextbook').hasClass("hide")) {
                $modal.find('.modal-body').find('.dontNeedTextbook').removeClass("hide")
              }
              $modal.find('.modal-body').find('.dontNeedTextbook').text("不需要教材");
            }
          } else {
            if (!$modal.find('.modal-body').find('#textbook-table').hasClass("hide")) {
              $modal.find('.modal-body').find('#textbook-table').addClass("hide")
            }
            if ($modal.find('.modal-body').find('.dontNeedTextbook').hasClass("hide")){
              $modal.find('.modal-body').find('.dontNeedTextbook').removeClass("hide")
            }
            $modal.find('.modal-body').find('.dontNeedTextbook').text("未指定教材");
          }*/

          $modal.find('.modal-body').find('.textbook').empty();
          $modal.find('.modal-body').find('.textbook').append(res.textbook ? res.textbook : '');

          $modal.find('.course-info-introduction').find('.introduction-nameZh').html(res.introduction ? res.introduction.nameZh : '');
          $modal.find('.course-info-introduction').find('.introduction-nameEn').html(res.introduction ? res.introduction.nameEn : '');

          $modal.find('.course-info-introduction').find('.lessonRemark').html(res.lessonRemark ? res.lessonRemark : '');

          $modal.modal('show');
        }
      });

      // 切换到"选课说明"标签页时按需加载选课说明数据（同一课程仅加载一次，避免重复发请求）
      $modal.find('.course-detail-tabs a[href="#course-remark-tab"]').off('shown.bs.tab.remark').on('shown.bs.tab.remark', function () {
        if ($modal.data('remarkLoadedLessonId') === lessonId) {
          return;
        }
        _self.showRemark(options, lessonId, true);
        $modal.data('remarkLoadedLessonId', lessonId);
      });

      $modal.unbind('hidden.bs.modal').on('hidden.bs.modal', function (e) {
        // 关闭弹窗时重置tab到"课堂信息"，避免下次打开时停在旧tab显示旧数据
        $modal.find('.course-detail-tabs li').removeClass('active');
        $modal.find('.course-detail-tabs a[href="#course-info-tab"]').parent().addClass('active');
        $modal.find('.course-detail-tabs').next('.tab-content').find('.tab-pane').removeClass('active');
        $modal.find('#course-info-tab').addClass('active');
        $modal.removeData('remarkLoadedLessonId');
        if ($('.modal.fade.in').length > 0) {
          $("body").addClass('modal-open');
        }
      });
    },

    allLessonConditionPullOpe: function () {

      $("#all-lessons").find(".show-or-hide-condition").unbind('click').on('click', function () {
        var $target = $(this).parents("#all-lessons").find(".level1-row");
        if ($target.hasClass('hide')) {
          $target.removeClass('hide');
          $(this).find('i').removeClass('fa-angle-double-down').addClass('fa-angle-double-up');
        } else {
          $target.addClass('hide');
          $(this).find('i').addClass('fa-angle-double-down').removeClass('fa-angle-double-up');
        }
      });
    },

    initAllLessonConditions: function (options, $table) {

      var _self = this;
      _self.renderDepartments($("#all-lessons").find(".open-department"), _self.openDepartments);
      _self.renderConditions($("#all-lessons").find(".campus"), _self.campus);
      _self.renderConditions($("#all-lessons").find(".class-type"), _self.classTypes);

      /*_self.renderConditions($("#all-lessons").find(".filter-units"), _self.courseUnits);*/
      _self.renderConditions($("#all-lessons").find(".course-education"), _self.courseEducations);
      // _self.renderConditions($("#all-lessons").find(".course-gradation"), []);

      //上课星期 节次
      $(".selectpicker").selectpicker('val', '');
      $(".selectpicker").selectpicker('refresh');

      //初始化学历层次
      $("#all-lessons").find(".course-education")[0].selectize.addItem(options.defautEducationId);


      function getCourseGradation() {
        var courseGradations = [];
        $.each(courseGradationList, function () {
          if (this.educationId == $("#all-lessons").find(".course-education").val()) {
            courseGradations.push(this);
          }
        });
        _self.renderConditions($("#all-lessons").find(".course-gradation"), courseGradations);
      }

      //根据条件查询
      _self.queryByConditions($table, $("#all-lessons"));

      //上课星期查询
      /*$(".filter-weekday").on('change', function () {
        if ($(this).val() == '') {
          _self.filterColumnWildcard($table, 11, '');
        } else {
          _self.filterColumnWildcard($table, 11, ': ' + $(this).val());
        }
      });*/

      //模糊查询：仅在“输入文字”时刷新人数；删除文字（退格/剪切）不刷新 std-count 请求
      var $globalFilter = $('input.global_filter');
      var doGlobalFilter = _.debounce(function () {
        _self.filterGlobal($table);
      }, 500);
      $globalFilter.on('input', function (e) {
        if (e.isComposing) return;
        doGlobalFilter();
      });
      $globalFilter.on('compositionend', function () {
        doGlobalFilter();
      });

      //自动判断可选课程
      $(".predicate").on('click', function () {
        if ($(".predicate").is(":checked")) {
          _self.addPredicate(options, $table);
        } else {
          $.each($table.find("tbody>tr"), function () {
            var $button = $(this).find('.course-select');
            $button.attr('disabled', false);
          });
        }
      });

      $.each($(".selectized"), function () {
        var _self_1 = this;
        if ($(_self_1)[0].tagName == 'SELECT') {
          $(_self_1)[0].selectize.on('focus', function () {
            if ($(_self_1)[0].selectize.getValue() == '') {
              $(_self_1)[0].selectize.clear();
            }
          });
        }
      });
    },

    renderConditions: function ($target, datas) {
      if ($target[0].selectize) {
        $target[0].selectize.destroy();
      }
      $target.selectize();
      $target[0].selectize.clearOptions();
      $target[0].selectize.clear();

      $target[0].selectize.addOption({value: '', text: '...'});
      $.each(datas, function () {
        $target[0].selectize.addOption({value: this.value, text: this.text});
      });
      $target[0].selectize.addItem('');
    },
    renderDepartments: function ($target, datas) {

      var departmentObj = departmentJs(datas);
      if ($target.attr('multiple') != 'multiple') {

      } else {

        if (departmentObj.departmentSortByCollege.length != 0) {
          $.each(departmentObj.departmentSortByCollege, function (index, item) {
            $target.append('<option value="' + item.id + '">' + item.code + '：' + item.name + '</option>');
          });
        }

        $target.selectpicker({
          dropupAuto: false,
          liveSearch: true,
          noneSelectedText: '支持多选'
        });
      }
      selectpickerDropdownOPe(departmentObj.collegeList, $target);

    },

    queryByConditions: function ($table, $target) {

      this.bindChangeOpe($table, $target.find(".class-type"), 11);

      $target.on("click", "#search-btn", function () {
        var courseEducation = $target.find(".course-education")[0];
        var filterUnits = $target.find(".filter-units")[0];
        var campus = $target.find(".campus")[0];
        var filterWeekday = $target.find(".filter-weekday")[0];

        var weekdays = '';
        if ($(filterWeekday).val()) {
          $.each($(filterWeekday).val(), function (index, item) {
            weekdays += '(?=.*(?:^|[: ])' + item + '(?:[\\(]))';
          });
          weekdays += '(^.*$)';
        }

        var openDepartments = '';
        var title = $(".open-department button").attr("title");
        if (title && title != "支持多选") {
          var departments = title.split(",");
          $.each(departments, function (index, item) {
            var arr = item.split("：")
            if (index + 1 == departments.length) {
              openDepartments += arr[1];
            } else {
              openDepartments += arr[1] + '|';
            }
          })
        }

        var units = '';
        if ($(filterUnits).val()) {
          $.each($(filterUnits).val(), function (index, item) {
            units += '(?=.*(?:^|[,])_' + item + '(?:[,]|$))';
          });
          units += '(^.*$)';
        }

        $table.DataTable().column(6).search(openDepartments, true, false)
            .column(8).search($(courseEducation).val() ? '^' + $(courseEducation).text() + '$' : '', true, false)
            .column(14).search($(campus).val() ? '^' + $(campus).text() + '$' : '', true, false)
            .column(11).search(weekdays, true, false)
            .column(12).search(units, true, false)
            .draw();

      });
      $target.on("click", "#reset-btn", function () {
        $("select.selectpicker").each(function () {
          $(this).selectpicker('val', '');
        });

        $target.find(".course-education")[0].selectize.clear();
        $target.find(".campus")[0].selectize.clear();

        $("select.selectpic").each(function () {
          $(this).selectpicker('val', '');
        });
        $(".open-department button").attr("title","支持多选");

      });
    },

    bindChangeOpe: function ($table, $target, i) {
      var _self = this;
      $target.on('change', function () {
        if ($(this).val() == '') {
          _self.filterColumn($table, i, '');
        } else {
          _self.filterColumn($table, i, $(this).text());
        }
      });
    },

    bindChangeOpeByWildcard: function ($table, $target, i) {
      var _self = this;
      $target.on('change', function () {
        if ($(this).val() == '') {
          _self.filterColumnWildcard($table, i, '');
        } else {
          _self.filterColumnWildcard($table, i, $(this).text());
        }
      });
    },

    getPredicateResults: function (options) {
      var key = "cs-predicateResults-" + options.studentId + "-" + options.turnId;
      return this.cache.getCache(key);
    },

    updatePredicateResults: function (options, newPredicateResults) {
      var key = "cs-predicateResults-" + options.studentId + "-" + options.turnId;
      this.cache.putCache(key, newPredicateResults, this.TTL_mills);
    },

    addPredicate: function (options, $table) {

      var _self = this;
      var lessons = this.getCurrentPageWithAddLessons();

      var predicateResult = _self.getPredicateResults(options);
      var currentDate = new Date().getTime();
      var hasNoStorageLessons = [];
      if (predicateResult != null) {
        var lessonIds = _.map(predicateResult.data, function (predicate) {
          return parseInt(predicate.lessonId)
        });
        hasNoStorageLessons = _.difference(lessons, lessonIds);
        if (hasNoStorageLessons.length == 0 && (currentDate - predicateResult.createDate <  _self.TTL_mills)) {
          return false;
        }
      }

      var _self = this;
      var checkbox = $(".predicate").get(0);
      if (!checkbox.checked) {
        return;
      }

      if (hasNoStorageLessons.length == 0 && lessons.length == 0) {
        return;
      }

      $.ajax({
        url: window.CONTEXT_PATH + options.url.sendAddPredicate,
        type: 'post',
        data: {
          studentAssoc: options.studentId,
          lessonAssocSet: (hasNoStorageLessons.length > 0) ? hasNoStorageLessons.join(',') : lessons.join(','),
          courseSelectTurnAssoc: options.turnId
        },
        success: function (res) {
          _self.getPredicateResponse(options, res, $table, "add");
        }
      });
    },

    dropPredicate: function (options, $table) {
      var lessons = this.getCurrentPageWithSelectedLessons();
      if (lessons.length <= 0) {
        return false;
      }

      var _self = this;
      $.ajax({
        url: window.CONTEXT_PATH + options.url.sendDropPredicate,
        type: 'post',
        data: {
          studentAssoc: options.studentId,
          lessonAssocSet: lessons.join(','),
          courseSelectTurnAssoc: options.turnId
        },
        success: function (res) {
          _self.getPredicateResponse(options, res, $table, "drop");
        }
      });
    },

    getPredicateResponse: function (options, requestId, $table, type) {

      var studentId = options.studentId;
      var _self = this;

      var count = 0;

      var func = function () {
        $.ajax({
          url: window.CONTEXT_PATH + options.url.fetchPredicateResult,
          type: 'post',
          data: {
            studentId: studentId,
            requestId: requestId
          },
          success: function (result) {

            count++;
            if (result) {

              if (!result.success) {
                return;
              }
              if (!result.result) {
                return;
              }
              var predicateResults = _self.getPredicateResults(options);

              var existLessons = [];
              var existLessonIds = [];
              if (predicateResults != null) {
                existLessons = predicateResults.data;
                existLessonIds = _.map(predicateResults.data, function (predicate) {
                  return predicate.lessonId
                });
              }

              var newPredicateResults = {};
              $.each(result.result, function (key, value) {
                var $button = $table.find('.course-select[data-id=' + key + ']');
                $button.attr('disabled', !value?'disabled':!value);

                if (existLessonIds.indexOf(key) != -1) {
                  var exitLesson = _.find(existLessons, function (lesson) {
                    return lesson.lessonId == key;
                  });

                  if (type == 'add') {
                    exitLesson['addable'] = value;
                  } else if (type == 'drop') {
                    exitLesson['droppable'] = value;
                  }
                } else {
                  existLessons.push({
                    lessonId: key,
                    addable: (type == 'add') ? value : true,
                    droppable: (type == 'drop') ? value : true,
                  });
                }
              });

              newPredicateResults['data'] = existLessons;
              _self.updatePredicateResults(options, newPredicateResults);
            } else {
              if (count < 10) {
                setTimeout(func, 1500);
              }
            }
          }
        });
      };

      setTimeout(func, 1000);

    },

    //选课
    courseSelect: function ($table, options) {
      this.requireConfig();

      var _self = this;
      require([
        'text!template/schedule-groups-modal.html'
      ], function (scheduleGroupModalTemp) {

        //选课
        $table.find(".course-select").unbind('click').on('click', function () {
          _self.selectScheduleGroup(options, $(this), scheduleGroupModalTemp);
        });
      });
    },

    addCourseRequest: function (options, $item, scheduleGroupId) {
      var _self = this;
      var lessonId = $item.data('id');

      // 防重复提交：请求进行中则直接返回
      if (_self._submitting) return;
      _self._submitting = true;

      // 禁用触发的选课按钮，防止重复点击
      var $triggerBtn = $item;
      $triggerBtn.prop('disabled', true).addClass('disabled');

      // 选课流程结束后的恢复逻辑（防重复调用）
      var restored = false;
      var restoreAfterSubmit = function () {
        if (restored) return;
        restored = true;
        _self._submitting = false;
        $triggerBtn.prop('disabled', false).removeClass('disabled');
      };

      // 全局超时兜底：10秒后强制恢复，防止异常请求卡死按钮
      var submitTimeout = setTimeout(function () {
        if (!restored) {
          $(".add-response .waiting-response").hide();
          $(".add-response .result-content").text("请求超时，请重试");
          $(".add-response .close-modal").attr('disabled', false);
          restoreAfterSubmit();
        }
      }, 10000);

      $.ajax({
        url: window.CONTEXT_PATH + options.url.sendAddRequest,
        type: 'post',
        data: {
          studentAssoc: options.studentId,
          lessonAssoc: lessonId,
          courseSelectTurnAssoc: options.turnId,
          scheduleGroupAssoc: scheduleGroupId,
          virtualCost: options.turnMode.enableVirtualWallet ? $("#virtualCost").val() : 0
        },
        success: function (res) {
          $('.add-response').modal({
            backdrop: false
          });
          $(".add-response .course-selection-apply").addClass('hide');

          var count = 0;

          var func = function () {
            $.ajax({
              url: window.CONTEXT_PATH + options.url.fetchAddDropResult,
              type: 'post',
              data: {
                studentId: options.studentId,
                requestId: res
              },
              success: function (result) {
                count++;
                if (result) {
                  $(".add-response .waiting-response").hide();

                  _self.dirtySelectLessons(options);

                  if (result.success) {
                    $(".add-response .result-content").text("选课成功");

                    //改变状态
                    var $btn = $item.parents('tr').find('td').eq(0).find('button');
                    var $label = $item.parents('tr').find('td').eq(1).find('label');
                    var $stdCount = $item.parents('tr').find('.std-count');
                    var enablePreSelect = $item.parents('tr').find(".course-enablePreSelect").val();

                    //解绑退课事件
                    $btn.unbind('click');
                    $btn.addClass('drop-course').removeClass('course-select').text("退课");
                    /* 选课成功：按钮保持蓝色，悬停时CSS自动变红 @Author: Jun.Wang @Date: 2026-06-14 */

                    //enablePreSelect(true就是预选, false就是正选)
                    if (enablePreSelect == 'true') {
                      $label.addClass('pinned-label').removeClass('drop-label').removeClass('select-label').text("待抽签");
                    } else {
                      $label.addClass('drop-label').removeClass('select-label').removeClass('pinned-label').text("已选中");
                    }

                    //已选学生数+1
                    // $stdCount.text($stdCount.text() >= $item.parents('tr').find(".limit-count") ?
                    //     $stdCount.text() : parseInt($stdCount.text()) + 1);

                    //重新绑定退课事件
                    _self.addDropLessonFunc(options, $item.parents('table'), 'out');

                    // 选课成功：清除超时定时器，恢复按钮
                    clearTimeout(submitTimeout);
                    restoreAfterSubmit();

                  } else {

                    var text = '';
                    if (window.LOCALE === 'zh') {
                      text = result.errorMessage.textZh;
                    } else {
                      text = result.errorMessage.textEn;
                    }

                    $(".add-response .result-content").text(text);

                    // 选课失败：恢复按钮
                    clearTimeout(submitTimeout);
                    restoreAfterSubmit();
                  }
                  $(".add-response .close-modal").attr('disabled', false);
                } else {
                  if (count < 10) {
                    setTimeout(func, 2000);
                  } else {
                    $(".add-response .waiting-response").hide();
                    $(".add-response .result-content").text("服务器繁忙,请稍候再试");
                    $(".add-response .close-modal").attr('disabled', false);
                    // 轮询超时：恢复按钮
                    clearTimeout(submitTimeout);
                    restoreAfterSubmit();
                  }
                }
              },
              error: function() {
                // 轮询请求本身失败（网络异常等），恢复按钮
                $(".add-response .waiting-response").hide();
                $(".add-response .result-content").text("网络异常，请重试");
                $(".add-response .close-modal").attr('disabled', false);
                clearTimeout(submitTimeout);
                restoreAfterSubmit();
              }
            });
          };

          setTimeout(func, 1000);
        },
        error: function(xhr, status, error) {
          $('.add-response').modal({backdrop: false});
          $(".add-response .course-selection-apply").addClass('hide');
          $(".add-response .waiting-response").hide();
          if (xhr.status === 429) {
            $(".add-response .result-content").text("请求过于频繁，请稍后再试");
          } else if (xhr.status === 403 || xhr.status === 401) {
            $(".add-response .result-content").text("访问受限，请稍后再试");
          } else if (xhr.status >= 500) {
            $(".add-response .result-content").text("服务器暂时不可用，请稍后再试");
          }

          $(".add-response .close-modal").attr('disabled', false);
          clearTimeout(submitTimeout);
          restoreAfterSubmit();
        }
      });
    },

    selectScheduleGroup: function (options, $item, scheduleGroupModalTemp) {
      var _self = this;
      var lessonId = $item.data('id');

      var lesson = _.filter(_self.fetchAddableLessons(options).data, function (suitableAddableLesson) {
        return suitableAddableLesson.id == lessonId;
      })[0];

      var scheduleGroupId = null;
      if (lesson.scheduleGroups.length > 1) {
        $(".schedule-groups .choose-schedule-group").show();

        var sortableScheduleGroups = _.sortBy(lesson.scheduleGroups, function (o) {
          return o.no;
        });
        $.each(sortableScheduleGroups, function () {
          this['isDefault'] = this.default;
          $(".choose-schedule-group").append(_.template(scheduleGroupModalTemp)(this));
        });

        $(".schedule-groups").modal('show');
        $(".schedule-groups .next-step").unbind('click').on('click', function () {

          $(".schedule-groups").modal('hide');
          scheduleGroupId = $(this).data('id');
          $(".schedule-groups .choose-schedule-group").hide();
          _self.addCourseRequest(options, $item, scheduleGroupId);

        });
      } else {
        _self.addCourseRequest(options, $item, scheduleGroupId);
      }

      $('.schedule-groups').unbind('hidden.bs.modal').on('hidden.bs.modal', function (e) {
        $(".choose-schedule-group").empty();
        $(".schedule-groups .choose-schedule-group").hide();
        $("#virtualCost").val('');
        if ($('.modal.fade.in').length > 0) {
          $("body").addClass('modal-open');
        }
      });

      $("#add-request").unbind("click").click(function () {
        if (!$("#virtualCostForm").valid()) {
          return false;
        }
        _self.addCourseRequest(options, $item, scheduleGroupId);
      });

      $('.add-response').unbind('hidden.bs.modal').on('hidden.bs.modal', function (e) {
        $(".add-response .waiting-response").show();
        $(".add-response .result-content").text("");
        $(".add-response .close-modal").attr('disabled', true);
        if ($('.modal.fade.in').length > 0) {
          $("body").addClass('modal-open');
        }
      });
    },

    /**
     * 开始定时刷新已选人数
     * @param options
     * @param $table
     * @param type
     */
    startRefreshStdCountPeriodically: function (options, $table) {
      var _self = this;
      _self.stopRefreshStdCountPeriodically();
      _self.startRefreshStdCount = true;
      var func = function () {
        if (!_self.startRefreshStdCount) return;
        _self.refreshStdCount(options, $table);
        _self._stdCountTimer = setTimeout(func, 20000);
      };
      _self._stdCountTimer = setTimeout(func, 20000);
    },

    /**
     * 停止刷新已选人数
     */
    stopRefreshStdCountPeriodically: function () {
      this.startRefreshStdCount = false;
      if (this._stdCountTimer) {
        clearTimeout(this._stdCountTimer);
        this._stdCountTimer = null;
      }
    },

    refreshStdCount: function (options, dataTable) {
      var _self = this;
      var now = Date.now();
      if (_self._stdCountFetching || now - (_self._stdCountLastFetchTime || 0) < 1000) return false;
      var currentPageLessons = this.getCurrentPageWithAddLessons(dataTable);
      if (currentPageLessons.length == 0) {
        return false;
      }
      _self._stdCountFetching = true;
      _self._stdCountLastFetchTime = now;
      $.ajax({
        url: window.CONTEXT_PATH + options.url.fetchStdCount,
        async: true,
        type: 'post',
        data: {lessonIds: currentPageLessons},
        success: function (res) {
          _self._stdCountFetching = false;
          for (var key in res) {
            var $stdCountDiv;
            if (dataTable && !!dataTable.length) {
              $stdCountDiv = dataTable.find('[data-id=' + key + ']').closest('tr');
            } else {
              $stdCountDiv = $(".dataTable:visible").find('[data-id=' + key + ']');
            }

            var count = res[key] > 0 ? res[key] : 0;

            if (options.turnMode.showCount) {
              var $stdCountProgress = $stdCountDiv.find('.std-count-progress');
              var limitCount = parseFloat($stdCountDiv.find('.limit-count').text());

              $stdCountProgress.css('width', limitCount == 0 ? '0%' : count / limitCount * 100 + '%');
              var $stdCount = $stdCountDiv.find('.std-count');
              $stdCount.text(count);

              if (limitCount <= count) {
                $stdCount.parents(".progress-text").addClass("text-danger");
                $stdCountProgress.removeClass('progress-bar-primary').addClass('progress-bar-danger');
              } else {
                $stdCount.parents(".progress-text").addClass("text-primary");
              }
            } else {
              var $stdCountNotShow = $stdCountDiv.find(".not-show-count");
              var limitCount = parseFloat($stdCountNotShow.attr("limit-count"));

              if (limitCount <= count) {
                $stdCountNotShow.addClass("text-danger");
                $stdCountNotShow.text("已满");
              } else {
                $stdCountNotShow.addClass("text-success");
                $stdCountNotShow.text("未满");
              }
            }
          }
        },
        error: function () {
          _self._stdCountFetching = false;
        }
      });
    },

    /**
     * 渲染已选课程
     * @param options
     * @param selectedLessonsTmpl
     */
    renderSelectedLessonTable: function (options, selectedLessonsTmpl) {
      var _self = this;
      this.find("#my-tab-content").append(_.template(selectedLessonsTmpl)());

      var selected_lesson_table = null;
      $('a.selected-lessons').on('shown.bs.tab', function (e) {

        //停止别的页面定时查询人数
        _self.stopRefreshStdCountPeriodically();

        var $selected_lesson_table = _self.find("#selected-lessons-table");
        if (selected_lesson_table) {
          selected_lesson_table.destroy();
          $selected_lesson_table.find('tbody').remove();
        }

        selected_lesson_table = $selected_lesson_table.DataTable({
          data: _self.fetchSelectedLessons(options).data,
          "autoWidth": false,
          "columns": [
            {
              "data": "id",
              /* 操作列：退课按钮 @Author: Jun.Wang @Date: 2026-06-13 */
              "render": function (field, row) {
                return '<button data-id="' + field.data + '" class="btn btn-primary drop-course" value="退课">退课</button>' /* 退课按钮默认蓝色，悬停变红 @Author: Jun.Wang @Date: 2026-06-14 */
                    +'<input type="hidden" value="'+ row.data.enablePreSelect +'" class="course-enablePreSelect"/>';
              }
            },
            {
              "data": "id",
              /* 选课状态：已选中(绿) / 待抽签(橙) @Author: Jun.Wang @Date: 2026-06-13 */
              "render": function (field, row) {
                if (!row.data.pinned) {
                  return '<label class="control-label pinned-label">待抽签</label>'
                }
                return '<label class="control-label drop-label">已选中</label>'
              }
            },
            /* 参选状态：隐藏原列 @Author: Jun.Wang @Date: 2026-06-13 */
            {"data": window.LOCALE != 'zh' ? "selectionState.nameEn" : "selectionState.nameZh", "visible": false},
            {
              "data": "code",
              /* 课堂号：隐藏原列，已合并到课程信息下方 @Author: Jun.Wang @Date: 2026-06-13 */
              "visible": false
            },
            {
              "data": window.LOCALE != 'zh' ? "course.nameEn" : "course.nameZh",
              /* 课程信息：放大加粗+info图标+悬停提示+点击弹信息，下方展示课堂号/学分/学历层次/校区（已选课程接口无授课语言字段，已移除）@Author: Jun.Wang @Date: 2026-06-14 */
              "render": function (field, row) {
                var lessonCode = row.data.code || '';
                var lessonId = row.data.id != null ? row.data.id : null;
                var credits = row.data.course.credits || '';
                var education = (row.data.courseGradation && row.data.courseGradation.education) ? row.data.courseGradation.education.nameZh : ''; /* 学历层次 @Author: Jun.Wang @Date: 2026-06-14 */
                var campus = _self.getDatasByList([row.data.campus], 'campus'); /* 校区 @Author: Jun.Wang @Date: 2026-06-14 */

                // 构建课程名称下方的标签行：课堂号 / 学分 / 学历层次 / 校区（已选课程接口无授课语言，已移除）@Author: Jun.Wang @Date: 2026-06-14
                var subInfo = '';
                var parts = [];
                if (lessonCode) parts.push(lessonCode);
                if (credits) parts.push(credits + '学分');
                if (education) parts.push(education);
                if (campus) parts.push(campus);
                var infoText = parts.join(' \u00A0|\u00A0 ');

                if (infoText) {
                  subInfo = '<div class="course-sub-info"> ' + infoText + ' </div>';
                }

                // 课程名称可点击，弹出课堂信息，info图标提示可点击 @Author: Jun.Wang @Date: 2026-06-13
                return '<div class="course-name-main click-course-info" data-id="' + lessonId + '" title="点击查看课程详情" style="cursor:pointer;">'
                  + field.data
                  + '<i class="fa fa-info-circle course-name-info-icon"></i>'
                  + '</div>' + subInfo;
              }
            },
            /* 学分：隐藏原列，已合并到课程信息下方 @Author: Jun.Wang @Date: 2026-06-13 */
            {"data": "course.credits", "visible": false},
            {"data": "openDepartment.nameZh"},
            /* 课程层次：单独显示为列（与全部课程标签页对齐）@Author: Jun.Wang @Date: 2026-06-14 */
            {"data": window.LOCALE != 'zh' ? "courseGradation.nameEn" : "courseGradation.nameZh"},
            {"data": "teacherStr",
              /* 授课教师：悬停提示+点击弹信息 @Author: Jun.Wang @Date: 2026-06-13 */
              "render": function (field, row) {
                var str = '<span class="teacher-name teacher-cell">'
                var teacherStr = '';
                $.each(row.data.teachers,function(i,item){
                  teacherStr += '<a class="click-teacher-info" lessonId="'+ row.data.id +'" data-id="' + item.id + '" title="点击查看教师详情">' + item.nameZh + '</a>,';
                })
                str += teacherStr.substring(0,teacherStr.length -1);
                str += '</span>';
                return str;
              }
            },
            /* 课程性质：隐藏原列，已合并到课程信息下方 @Author: Jun.Wang @Date: 2026-06-13 */
            {"data": window.LOCALE != 'zh' ? "selectionType.nameEn" : "selectionType.nameZh", "visible": false},
            /* 课堂类型：隐藏原列，已合并到课程信息下方 @Author: Jun.Wang @Date: 2026-06-13 */
            {"data": window.LOCALE != 'zh' ? "courseType.nameEn" : "courseType.nameZh", "visible": false},
            /* 课程层次已单独显示为列（开课单位与授课教师之间），此处不再重复定义 @Author: Jun.Wang @Date: 2026-06-14 */
            {
              "data": "weekDayPlaceText.textZh",
              /* 上课地点时间：按分号分隔换行，鼠标悬停按分号换行显示完整内容 @Author: Jun.Wang @Date: 2026-06-13 */
              "render": function (field, row) {
                var fullText = field.data || '';
                var displayHtml = fullText.replace(/;/g, ';<br/>');
                var titleText = fullText.replace(/;/g, ';\n');
                return '<span class="location-time-cell" title="' + titleText.replace(/"/g, '&quot;') + '">' + displayHtml + '</span>';
              }
            },
            {
              "data": "weekText.textZh",
              /* 起止周：按分号分隔换行，鼠标悬停按分号换行显示完整内容 @Author: Jun.Wang @Date: 2026-06-13 */
              "render": function (field, row) {
                var fullText = field.data || '';
                var displayHtml = fullText.replace(/;/g, ';<br/>');
                var titleText = fullText.replace(/;/g, ';\n');
                return '<span class="week-range-cell" title="' + titleText.replace(/"/g, '&quot;') + '">' + displayHtml + '</span>';
              }
            },
            {
              "data": "limitCount",
              "render": function(field) {
                if (options.turnMode.showCount) {
                  return '<div style="width: 80%">' +
                      '<div class="progress-text text-center"><span class="std-count">0</span>/<span class="limit-count">' + field.data + '</span></div>' +
                      '<div class="progress" style="height: 5px;">' +
                      '<div class="progress-bar progress-bar-primary std-count-progress" role="progressbar">' +
                      '</div></div>' +
                      '</div>';
                } else {
                  return '<div class="not-show-count" limit-count="'+field.data+'"></div>';
                }
              }
            },
            /* 备注：隐藏原列，已合并到课程信息下方 @Author: Jun.Wang @Date: 2026-06-13 */
            {"data": "remark",
              "visible": false,
              "render": function (field) {
                if (field.data) {
                  return '<span data-toggle="tooltip" data-placement="left" data-original-title="' + field.data + '" style="cursor: pointer;">' +
                      '<i class="fa fa-info-circle"></i></span>';
                }
              }
            }
          ],
          "aoColumnDefs": [{"orderable": false, "targets": [0, 1]}],
          "bPaginate": false,
          "dom": 'rt<"row"<"col-md-5"><"col-md-7"p>>',
          "language": {
            "info": "<a class='btn btn-default disabled'>_START_-_END_ of _TOTAL_</a>",
            "infoEmpty": "<a class='btn btn-default disabled'>_START_-_END_ of _TOTAL_</a>",
            "sInfoFiltered": "",
            "sZeroRecords": "无数据",
            "search": "<a><i class='fa fa-search'></i></a> _INPUT_ <br/>"
          },
          "drawCallback": function (settings) {

            $selected_lesson_table.find("thead>tr>th").eq(0).addClass("sorting_disabled").removeClass("sorting_asc").removeClass("sorting_desc");
            $selected_lesson_table.find("thead>tr>th").eq(1).addClass("sorting_disabled").removeClass("sorting_asc").removeClass("sorting_desc");

            _self.refreshStdCount(options, $selected_lesson_table);
            _self.addDropLessonFunc(options, $selected_lesson_table, 'in');
            $('[data-toggle="tooltip"]').tooltip({container: 'body'});

            $selected_lesson_table.find('.click-course-info').unbind('click').click(function () {
              _self.showCourseInfo(options, $(this).data('id'));
            });

            $selected_lesson_table.find('.click-teacher-info').unbind('click').click(function () {
              _self.showTeacherInfo(options, $(this).attr('lessonId') ,$(this).data('id'));
            });
          }
        });

        _self.startRefreshStdCountPeriodically(options, $selected_lesson_table);
      });

    },

    /**
     * 给教学任务添加退课的功能
     * @param options
     * @param selected_lesson_table
     */
    addDropLessonFunc: function (options, selected_lesson_table, type) {
      var _self = this;
      selected_lesson_table.find(".drop-course").unbind('click').on('click', function () {
        var $drop_course = $(this);
        var lessonId = $drop_course.data('id');
        bootbox.confirm({
          message: `<p style="font-size: 20px;">⚠️ 是否确认退课？退课后，如该课堂已满，<span style="font-size: 20px;color: red;font-weight: bold;">将无法再次选课！</span></p>`,
          buttons: {
            confirm: {
              label: '确认退课',
              className: 'btn-primary'
            },
            cancel: {
              label: '取消',
              className: 'btn-default'
            }
          },
          callback: function (result) {
            if (!result) {
              return;
            }
            $.ajax({
              url: window.CONTEXT_PATH + options.url.sendDropRequest,
              type: 'post',
              data: {
                studentAssoc: options.studentId,
                lessonAssoc: lessonId,
                courseSelectTurnAssoc: options.turnId
              },
              success: function (res) {
                $('.drop-response').modal({
                  backdrop: false
                });
                $(".drop-response .course-drop-apply").addClass('hide');

                var count = 0;
                var func = function () {
                  $.ajax({
                    url: window.CONTEXT_PATH + options.url.fetchAddDropResult,
                    type: 'post',
                    data: {
                      studentId: options.studentId,
                      requestId: res
                    },
                    success: function (result) {
                      count++;
                      if (result) {
                        $(".drop-response .waiting-response").hide();
                        _self.dirtySelectLessons(options);

                        if (result.success) {
                          $(".drop-response .result-content").text("退课成功");

                          if ('in' == type) {
                            //删除该列(已选课程tab)
                            selected_lesson_table.DataTable()
                                .row($drop_course.parents('tr'))
                                .remove()
                                .draw();
                          } else if ('out' == type) {
                            //改变状态
                            var $btn = $drop_course.parents('tr').find('td').eq(0).find('button');
                            var $label = $drop_course.parents('tr').find('td').eq(1).find('label');
                            var $stdCount = $drop_course.parents('tr').find('.std-count');

                            //解绑选课事件
                            $btn.unbind('click');
                            $btn.removeClass("drop-course").addClass("course-select").text("选课");
                            /* 退课成功：按钮保持蓝色，悬停时CSS自动变绿 @Author: Jun.Wang @Date: 2026-06-14 */
                            $label.removeClass('drop-label').removeClass('pinned-label').addClass('select-label1').text("");

                            //已选学生数-1
                            // $stdCount.text($stdCount.text() > 0 ? $stdCount.text() - 1 : $stdCount.text());

                            //重新绑定选课事件
                            _self.courseSelect(selected_lesson_table, options);

                          }
                        } else {

                          var text = '';
                          if (window.LOCALE === 'zh') {
                            text = result.errorMessage.textZh;
                          } else {
                            text = result.errorMessage.textEn;
                          }

                          $(".drop-response .result-content").text(text);

                        }
                        $(".drop-response .close-modal").attr('disabled', false);
                      } else {
                        if (count < 10) {
                          setTimeout(func, 2000);
                        } else {
                          $(".drop-response .waiting-response").hide();
                          $(".drop-response .result-content").text("服务器繁忙,请稍候再试");
                          $(".drop-response .close-modal").attr('disabled', false);
                        }
                      }
                    },
                    error: function () {
                      $(".drop-response .waiting-response").hide();
                      $(".drop-response .result-content").text("网络异常，请重试");
                      $(".drop-response .close-modal").attr('disabled', false);
                    }
                  });
                };
                setTimeout(func, 1000);
              }
            });
          },
          onShown: function(dialog) {
            // 监听键盘事件
            dialog.on('keypress', function(e) {
              // 如果按下 Enter 键 (keyCode 13)
              if (e.which === 13) {
                // 触发取消按钮点击
                dialog.find('.btn-default').trigger('click');
                e.preventDefault();
                return false;
              }
            });
          }
        });
      });

      $('.drop-response').unbind('hidden.bs.modal').on('hidden.bs.modal', function (e) {
        $(".drop-response .waiting-response").show();
        $(".drop-response .result-content").text("");
        $(".drop-response .close-modal").attr('disabled', true);
        if ($('.modal.fade.in').length > 0) {
          $("body").addClass('modal-open');
        }
      });
    }
  });

});
