(function () {
  var style = getComputedStyle(document.documentElement);
  var accent = style.getPropertyValue('--accent').trim();
  var accent2 = style.getPropertyValue('--accent2').trim();
  var ink = style.getPropertyValue('--ink').trim();
  var muted = style.getPropertyValue('--muted').trim();
  var rule = style.getPropertyValue('--rule').trim();
  var bg2 = style.getPropertyValue('--bg2').trim();
  var info = style.getPropertyValue('--info').trim();
  var danger = style.getPropertyValue('--danger').trim();
  var tA = '#1a7a3a', tB = '#2b6cb0', tC = '#c08a1e', tD = '#8b5cf6', tE = '#ec6f3d', tF = '#5c6b5e';

  var ganttEl = document.getElementById('chart-gantt');
  if (ganttEl) {
    var gantt = echarts.init(ganttEl, null, { renderer: 'svg' });

    var categories = [
      'F: UI与平台',
      'E: 蝴蝶效应',
      'D: 世界系统',
      'C: 经营系统',
      'B: 赛季框架',
      'A: 引擎核心'
    ];

    // [startWeek, duration, trackName, color, gateLabel]
    var tasks = [
      // Track A: Engine core (weeks 1-14, but Gate 1 at week 6)
      { start: 1, dur: 6, cat: 5, label: 'A1: 比赛引擎+成长+存档', color: tA },
      { start: 6, dur: 2, cat: 5, label: 'A2: Gate1 修复+优化', color: tA + '88' },
      { start: 8, dur: 8, cat: 5, label: 'A3: 引擎完善+支持', color: tA + 'aa' },

      // Track B: Season framework (weeks 1-16, Gate 2 at week 14)
      { start: 1, dur: 6, cat: 4, label: 'B1: 赛程+阵容+战术(独立编码)', color: tB + 'aa' },
      { start: 7, dur: 7, cat: 4, label: 'B2: 集成赛季循环', color: tB },
      { start: 14, dur: 2, cat: 4, label: 'B3: Gate2 修复', color: tB + '88' },

      // Track C: Business systems (weeks 1-16)
      { start: 1, dur: 6, cat: 3, label: 'C1: 转会+合同+青训(独立编码)', color: tC + 'aa' },
      { start: 7, dur: 7, cat: 3, label: 'C2: 集成经营系统', color: tC },
      { start: 14, dur: 4, cat: 3, label: 'C3: 球探+历史新星完善', color: tC + 'aa' },

      // Track D: World systems (weeks 1-30, Gate 3 at week 28)
      { start: 1, dur: 5, cat: 2, label: 'D1: AI决策+经济(设计+原型)', color: tD + '88' },
      { start: 6, dur: 10, cat: 2, label: 'D2: AI决策编码', color: tD + 'aa' },
      { start: 16, dur: 12, cat: 2, label: 'D3: AI+经济集成', color: tD },
      { start: 28, dur: 4, cat: 2, label: 'D4: Gate3 修复', color: tD + '88' },

      // Track E: Butterfly effect (weeks 6-30)
      { start: 6, dur: 6, cat: 1, label: 'E1: 蝴蝶效应逻辑编码', color: tE + 'aa' },
      { start: 16, dur: 12, cat: 1, label: 'E2: 蝴蝶效应集成', color: tE },
      { start: 28, dur: 4, cat: 1, label: 'E3: Gate3 修复', color: tE + '88' },

      // Track F: UI & Platform (weeks 1-36)
      { start: 1, dur: 5, cat: 0, label: 'F1: Compose框架+版权UI', color: tF },
      { start: 6, dur: 10, cat: 0, label: 'F2: 新手引导+数据导入', color: tF + 'aa' },
      { start: 16, dur: 12, cat: 0, label: 'F3: 沉浸系统+本地化', color: tF },
      { start: 28, dur: 8, cat: 0, label: 'F4: 性能优化+收尾', color: tF + '88' }
    ];

    var data = tasks.map(function (t) {
      return {
        value: [t.start, t.dur, t.cat, t.label],
        itemStyle: { color: t.color }
      };
    });

    // Gate markers
    var gateLines = [
      { xAxis: 6, name: 'Gate 1' },
      { xAxis: 14, name: 'Gate 2' },
      { xAxis: 28, name: 'Gate 3' }
    ];

    gantt.setOption({
      animation: false,
      tooltip: {
        trigger: 'item',
        appendToBody: true,
        formatter: function (p) {
          var v = p.data.value;
          return '<b>' + v[3] + '</b><br/>第 ' + v[0] + ' 周，持续 ' + v[1] + ' 周';
        }
      },
      grid: { left: 100, right: 30, top: 40, bottom: 56 },
      xAxis: {
        type: 'value',
        name: '周',
        nameLocation: 'middle',
        nameGap: 30,
        nameTextStyle: { color: muted, fontSize: 12, fontWeight: 600 },
        min: 0,
        max: 38,
        splitLine: { show: false },
        axisLine: { lineStyle: { color: rule } },
        axisLabel: { color: muted, fontSize: 11 },
        axisTick: { show: false }
      },
      yAxis: {
        type: 'category',
        data: categories,
        axisLine: { lineStyle: { color: rule } },
        axisLabel: { color: ink, fontSize: 11, fontWeight: 600 },
        axisTick: { show: false }
      },
      series: [{
        type: 'custom',
        renderItem: function (params, api) {
          var start = api.coord([api.value(0), api.value(2)]);
          var endCoord = api.coord([api.value(0) + api.value(1), api.value(2)]);
          var height = api.size([0, 1])[1] * 0.5;
          return {
            type: 'rect',
            shape: {
              x: start[0],
              y: start[1] - height / 2,
              width: Math.max(endCoord[0] - start[0], 2),
              height: height,
              r: 3
            },
            style: api.style()
          };
        },
        data: data,
        label: {
          show: true,
          position: 'insideLeft',
          formatter: function (p) {
            var v = p.data.value;
            return v[3].length > 18 ? v[3].substring(0, 17) + '…' : v[3];
          },
          color: '#fff',
          fontSize: 9,
          fontWeight: 600,
          distance: 4,
          overflow: 'truncate',
          width: 120
        }
      }],
      markLine: {
        silent: true,
        symbol: ['none', 'none'],
        lineStyle: { color: danger, type: 'dashed', width: 2 },
        label: {
          show: true,
          position: 'end',
          formatter: function (p) {
            return gateLines[p.dataIndex].name;
          },
          color: danger,
          fontSize: 11,
          fontWeight: 700
        },
        data: gateLines.map(function (g) {
          return { xAxis: g.xAxis };
        })
      }
    });
    window.addEventListener('resize', function () { gantt.resize(); });
  }
})();
