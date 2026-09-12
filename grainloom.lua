-- grainloom: a minimal continuous feedback loop machine
-- SPDX-License-Identifier: MIT
engine.name = 'Grainloom'

local cs = require 'controlspec'

local function pid(id) return 'grainloom_'..id end
local function pget(id) return params:get(pid(id)) end

local pages = {
  {'LOOP', 'capture_length', 'feedback'},
  {'WINDOW', 'window_start', 'window_size'},
  {'TAPE', 'rate', 'mix'},
  {'REGEN', 'regen', 'regen_tone'},
  {'SLICE', 'slice_size', 'slice_density'},
  {'SLICE POS', 'slice_age', 'slice_spread'},
  {'SLICE PLAY', 'slice_speed', 'slice_reverse'},
  {'LEVEL', 'slice_mix', 'sample_gain'},
  {'BLOOM', 'bloom', 'bloom_time'}
}

-- The loop now reaches into audio rates, where seconds read as 0.0.
local function fmt_seconds(v)
  if v < 1 then return string.format('%.0fms', v*1000) end
  return string.format('%.1fs', v)
end

local page = 1
local state = 0 -- 0 empty, 1 write frozen, 2 dubbing, 4 recording
local seconds = 0
local revision = 0
local pending = true
local writing = true
local playing = true
local dubbing = false
local message = 'starting loop...'
local timed_out = false
local k2_handled = false
local k2_hold_time = 0.5
local state_poll, time_poll, refresh, mirror_clock, watchdog_clock, k2_hold

local function control(id, name, min, max, warp, default, units, action)
  params:add_control(pid(id), name,
    cs.new(min, max, warp, 0, default, units or ''))
  if action then params:set_action(pid(id), action) end
end

-- The engine allocates once, at load. Without a bound on `pending` a wedged
-- allocation would sit the UI in WAIT forever with K2/K3 dead until a reload.
local function arm_watchdog()
  if watchdog_clock then clock.cancel(watchdog_clock) end
  watchdog_clock = clock.run(function()
    clock.sleep(5)
    watchdog_clock = nil
    if pending then
      pending = false
      timed_out = true
      message = 'loop alloc timeout'
    end
  end)
end

function init()
  params:add_separator('grainloom')

  control('capture_length', 'recording time', 0.02, 30, 'exp', 2.5, 's')
  params:lookup_param(pid('capture_length')).formatter = function(param)
    return fmt_seconds(param:get())
  end
  control('feedback', 'feedback', 0, 0.98, 'lin', 0.72, '',
    function(v) engine.feedback(v) end)
  control('rate', 'tape speed', -2, 2, 'lin', 1, 'x',
    function(v) engine.rate(v) end)
  control('mix', 'input / sample mix', 0, 1, 'lin', 0.8, '',
    function(v) engine.mix(v) end)
  params:lookup_param(pid('mix')).formatter = function(param)
    local sample = math.floor(param:get()*10+0.5)
    return (10-sample)..':'..sample
  end
  control('sample_gain', 'sample level', 0.25, 4, 'exp', 1.5, 'x',
    function(v) engine.sample_gain(v) end)
  control('slice_size', 'slice size', 0.06, 0.5, 'exp', 0.2, 's',
    function(v) engine.slice_size(v) end)
  control('slice_density', 'slice density', 1, 8, 'exp', 5, 'Hz',
    function(v) engine.slice_density(v) end)
  control('slice_speed', 'quantized speed max', 0.5, 2, 'lin', 1.5, 'x',
    function(v) engine.slice_speed(v) end)
  control('slice_reverse', 'reverse chance', 0, 1, 'lin', 0.35, '',
    function(v) engine.slice_reverse(v) end)
  control('slice_mix', 'tape / slice mix', 0, 1, 'lin', 0.5, '',
    function(v) engine.slice_mix(v) end)
  control('gain', 'output level', 0, 1, 'lin', 0.75, '',
    function(v) engine.gain(v) end)
  control('dub_level', 'dub level', 0, 1, 'lin', 1, '',
    function(v) engine.dub_level(v) end)
  control('window_start', 'window start', 0, 1, 'lin', 0, '',
    function(v) engine.window_start(v) end)
  control('window_size', 'window size', 0.01, 1, 'exp', 1, '',
    function(v) engine.window_size(v) end)
  control('slice_age', 'slice age', 0, 1, 'lin', 0, '',
    function(v) engine.slice_age(v) end)
  control('slice_spread', 'slice spread', 0, 1, 'lin', 1, '',
    function(v) engine.slice_spread(v) end)
  control('bloom', 'bloom', 0, 1, 'lin', 0, '',
    function(v) engine.bloom(v) end)
  control('bloom_time', 'bloom time', 0.5, 10, 'exp', 4, 's',
    function(v) engine.bloom_time(v) end)
  control('regen', 'regen', 0, 1, 'lin', 0, '',
    function(v) engine.regen(v) end)
  control('regen_tone', 'regen tone', 200, 8000, 'exp', 4000, 'Hz',
    function(v) engine.regen_tone(v) end)

  params:set_action(pid('capture_length'), function(v)
    -- Length is a number both heads wrap on, so it lands on the next sample:
    -- no reallocation, no reset, no gap, and it can be swept while playing.
    engine.loopLength(v)
    seconds = v
    -- Only the mirror one loop ahead needs catching up, and only once the
    -- encoder settles. Reads inside the loop are correct the whole time.
    if mirror_clock then clock.cancel(mirror_clock) end
    mirror_clock = clock.run(function()
      clock.sleep(0.2)
      mirror_clock = nil
      engine.primeMirror()
    end)
  end)

  -- Initialize only Grainloom controls; never bang unrelated system params.
  engine.feedback(pget('feedback'))
  engine.rate(pget('rate'))
  engine.mix(pget('mix'))
  engine.sample_gain(pget('sample_gain'))
  engine.slice_size(pget('slice_size'))
  engine.slice_density(pget('slice_density'))
  engine.slice_speed(pget('slice_speed'))
  engine.slice_reverse(pget('slice_reverse'))
  engine.slice_mix(pget('slice_mix'))
  engine.gain(pget('gain'))
  engine.dub_level(pget('dub_level'))
  engine.window_start(pget('window_start'))
  engine.window_size(pget('window_size'))
  engine.slice_age(pget('slice_age'))
  engine.slice_spread(pget('slice_spread'))
  engine.bloom(pget('bloom'))
  engine.bloom_time(pget('bloom_time'))
  engine.regen(pget('regen'))
  engine.regen_tone(pget('regen_tone'))

  state_poll = poll.set('grainloom_state')
  state_poll.time = 0.1
  state_poll.callback = function(v)
    local rev = math.floor(v/10)
    state = v%10
    if rev > revision then
      -- Any settled state from the engine means the one-time allocation
      -- finished; length changes no longer pass through an allocating state.
      pending = false
      timed_out = false
      if state == 4 then
        writing = true
        dubbing = false
        message = 'recording + replaying'
      elseif state == 2 then
        writing = true
        dubbing = true
        message = 'dub rec'
      elseif state == 1 then
        writing = false
        dubbing = false
        message = 'freeze on'
      elseif state == 0 then
        dubbing = false
        message = 'loop stopped'
      end
    end
    revision = rev
  end

  time_poll = poll.set('grainloom_seconds')
  time_poll.time = 0.1
  time_poll.callback = function(v) seconds = v end
  state_poll:start()
  time_poll:start()

  arm_watchdog()
  engine.liveLoop(1, pget('capture_length'))
  refresh = metro.init(function() redraw() end, 1/15)
  refresh:start()
end

function enc(n, d)
  if n == 1 then
    page = (page - 1 + d) % #pages + 1
  elseif n == 2 or n == 3 then
    params:delta(pid(pages[page][n]), d)
  end
  redraw()
end

-- K2 carries two gestures, so the freeze toggle moved to the release: a short
-- press toggles freeze, holding it while frozen starts dub, and any press
-- during dub ends it. `k2_handled` marks a press already spent by one of the
-- latter two so the release does not also toggle.
local function k2_press()
  if pending then
    k2_handled = true
  elseif dubbing then
    dubbing = false
    engine.dubbing(0)
    message = 'dub off'
    k2_handled = true
  else
    k2_handled = false
    if k2_hold then clock.cancel(k2_hold) end
    k2_hold = clock.run(function()
      clock.sleep(k2_hold_time)
      k2_hold = nil
      -- Only a frozen loop can be dubbed. With the head running there is no
      -- settled pass to layer onto, so the hold falls through to the toggle.
      if not pending and not writing and not dubbing then
        dubbing = true
        k2_handled = true
        engine.dubbing(1)
        message = 'dub rec'
        redraw()
      end
    end)
  end
end

local function k2_release()
  if k2_hold then clock.cancel(k2_hold); k2_hold = nil end
  if not k2_handled then
    writing = not writing
    engine.writing(writing and 1 or 0)
    message = writing and 'freeze off' or 'freeze on'
  end
  k2_handled = false
end

function key(n, z)
  if n == 2 then
    if z == 1 then k2_press() else k2_release() end
  elseif n == 3 and z == 1 and not pending then
    playing = not playing
    engine.playing(playing and 1 or 0)
    message = playing and 'sample on' or 'sample off'
  end
  redraw()
end

function redraw()
  screen.clear()
  screen.aa(1)
  screen.font_size(8)
  screen.level(15)
  screen.move(0, 9)
  screen.text('grainloom')
  screen.move(128, 9)
  local status = pending and 'WAIT' or (writing and 'LOOP' or 'FREEZE')
  if not playing then status = 'OFF' end
  if dubbing then status = 'DUB' end
  if timed_out then status = 'ERR' end
  screen.text_right(status..' '..fmt_seconds(seconds))

  screen.level(5)
  screen.move(0, 22)
  screen.text(page..'/'..#pages..' '..pages[page][1])
  for i=2,3 do
    local id = pages[page][i]
    screen.level(12)
    screen.move(0, 20+i*10)
    screen.text(params:lookup_param(pid(id)).name)
    screen.move(128, 20+i*10)
    screen.text_right(params:string(pid(id)))
  end

  screen.level(5)
  screen.move(0, 63)
  local hint = 'K2 freeze     K3 on/off'
  if dubbing then
    hint = 'K2 end dub    K3 on/off'
  elseif not writing and not pending then
    hint = 'K2 hold: dub  K3 on/off'
  end
  screen.text(hint)
  screen.update()
end

function cleanup()
  if mirror_clock then clock.cancel(mirror_clock); mirror_clock = nil end
  if watchdog_clock then clock.cancel(watchdog_clock); watchdog_clock = nil end
  if k2_hold then clock.cancel(k2_hold); k2_hold = nil end
  if refresh then refresh:stop() end
  if state_poll then state_poll:stop() end
  if time_poll then time_poll:stop() end
  -- norns calls cleanup() even when init() failed part-way, so the paramset
  -- may never have been populated: reading a param here would raise and the
  -- stop command would never be sent. The engine ignores the length argument
  -- when the first one is 0.
  if engine.liveLoop then engine.liveLoop(0, 0) end
end
