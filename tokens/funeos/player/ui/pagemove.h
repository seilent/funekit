#pragma once
#include <climits>
#include <cstdint>
#include <vector>

#include "../core/curves.h"
#include "../core/fx.h"

namespace fd {

enum class MoveKind : uint8_t { T2, T2n, T4h, T4n, T3, T4, T3m, T4m, T7, T8, T3c, T3a, T4a, T3z, T4z, None };

enum class Part : uint8_t {
    Leave,
    LeaveHeader,
    LeavePivots,
    LeaveGround,
    LeaveWord,
    Content,
    Header,
    Zoom,
    Pivots,
    Ground,
    Word,
    Look,
    Home,
    Sub,
    Peek,
    Tile,
    NpIn,
    NpOut,
    NpLeave,
    NpEdge,
    NpPeek,
    NpUnder,
    NpPhoto,
    NpRing,
    NpArtist,
    NpAlbum,
    NpTitle,
    NpRings,
    NpTimeR,
    NpTimeL,
    NpBar,
    NpShuffle,
    NpNext,
    NpStatus,
    NpUHeader,
    NpURow0,
    NpURow1,
    NpURow2,
    NpURow3,
    NpURows,
    NpUArtist,
    NpLArtist,
    NpLRow1,
    NpLRows,
    NpLHead,
    NpLPivot,
    NpLName,
    NpLRing0,
    NpLRing1,
    NpLRing2,
    NpLLabel,
    NpLPress,
    Face,
    HomeTurn,
    Count
};

static_assert((int)Part::Count <= 64, "part masks are 64 bit");

enum class Prop : uint8_t { A, S, X, Y, R, PX, PY, Count };

constexpr uint64_t part_bit(Part p) { return (uint64_t)1 << (int)p; }

template <class... P> constexpr uint64_t parts(P... p) { return (part_bit(p) | ... | 0); }

inline constexpr uint64_t kLeaveGroup =
    parts(Part::Leave, Part::LeaveHeader, Part::LeavePivots, Part::LeaveGround, Part::LeaveWord);
inline constexpr uint64_t kArriveGroup = parts(Part::Content, Part::Header, Part::Pivots);
inline constexpr uint64_t kNpInGroup =
    parts(Part::NpIn, Part::NpRing, Part::NpArtist, Part::NpAlbum, Part::NpTitle, Part::NpRings, Part::NpTimeR,
          Part::NpTimeL, Part::NpBar, Part::NpShuffle, Part::NpNext, Part::NpStatus, Part::NpPhoto);
inline constexpr uint64_t kNpUnderGroup = parts(Part::NpUnder, Part::NpUHeader, Part::NpURow0, Part::NpURow1,
                                                 Part::NpURow2, Part::NpURow3, Part::NpURows, Part::NpUArtist);
inline constexpr uint64_t kWordGroup = parts(Part::Word, Part::LeaveWord);

enum class ContentForm : uint8_t { Zoom, Step, Rest };

enum class WordLeg : uint8_t { HomeOpen, HomeBack, Push, Pop, Grow, Shrink };

enum class MorphKind : uint8_t { None, Pivot, Row };

enum class NpForm : uint8_t { Page, Front, Ring };

enum class NpLeave : uint8_t { Pane, Album, Detail };

struct Leg {
    Part part = Part::Leave;
    Prop prop = Prop::A;
    fx from = 0, to = 0;
    int delay = 0, dur = 0;
    Curve curve = Curve::Linear;
    int pin = 0;

    fx at(int64_t e) const;
    int64_t end() const { return (int64_t)delay + dur; }
};

struct Key {
    int t = 0;
    fx v = 0;
};

inline constexpr fx kPopPitch0 = fx_int(984) / 1000, kPopDrop0 = fx_int(79) / 10;
inline constexpr int kT4nItems = 133, kT4nOut = 167;

constexpr fx key_fx(double v) { return (fx)(v * 65536 + (v < 0 ? -0.5 : 0.5)); }

inline constexpr fx kT4nInEye = key_fx(0.498);

inline constexpr Key kT4hPitch[] = {{0, key_fx(0.975)}, {17, key_fx(0.94)}, {50, key_fx(0.928)},
                                    {67, key_fx(0.912)}, {83, key_fx(0.90)}, {100, key_fx(0.885)}};
inline constexpr Key kT4RowDy[] = {{0, key_fx(7.9)},   {33, key_fx(21.8)},  {50, key_fx(27.8)}, {67, key_fx(33.7)},
                                   {83, key_fx(39.0)}, {100, key_fx(43.7)}, {117, key_fx(47.6)}};
inline constexpr Key kT4RowPitch[] = {{0, key_fx(0.984)},  {33, key_fx(0.942)},  {50, key_fx(0.930)},
                                      {67, key_fx(0.914)}, {83, key_fx(0.903)},  {100, key_fx(0.891)},
                                      {117, key_fx(0.883)}};

struct MoveForms {
    bool leave_fade = true;
    ContentForm content = ContentForm::Zoom;
    bool chrome_slide = true;
    bool header_zoom = true;
    bool t3m_fade = true;
    bool word_morph = true;
    ContentForm hub_rows = ContentForm::Zoom;
    ContentForm page_rows = ContentForm::Zoom;

    static MoveForms board();
};

struct MoveEnds {
    fx pivot_x = 0, pivot_y = 0;
    fx word_s0 = kOne, word_s1 = kOne, word_x0 = 0, word_x1 = 0, word_y0 = 0, word_y1 = 0;
    fx tile_s0 = kOne, tile_s1 = kOne, tile_x0 = 0, tile_x1 = 0, tile_y0 = 0, tile_y1 = 0;
    fx peek0 = 0, peek1 = 0, sub = kOne, look = kOne;
    fx morph_s0 = kOne, morph_s1 = kOne, morph_x0 = 0, morph_x1 = 0, morph_y0 = 0, morph_y1 = 0;
    bool morph = false, morph_header_in = false, morph_header_out = false;
    bool grounds = false;
    bool zoom = true;
    bool pivots = true;
    bool word = false;
    MorphKind morph_kind = MorphKind::None;
    bool rows = false;
    bool leave_rows = false;
    bool retarget = false;
    fx rows_dx = 0;
    NpForm np_form = NpForm::Page;
    NpLeave np_leave = NpLeave::Pane;
    bool np_cut = false, np_home = false, plug = false, np_queue = false, turn = false;
    bool col_hold = false, tile_back = false, col_back = false, thumb = false;
    fx px_u = kOne, home_u = kOne, sw = 0, sh = 0;
    fx zoom_x = 0, zoom_y = 0, zoom_base = 0, lhead_base = 0, tap_x = 0, tap_y = 0;
    bool lhead = false;
};

class PageMove {
public:
    void start(MoveKind k, int64_t t0, const MoveEnds& e, const MoveForms& f);
    void stop() { live_ = false; }
    bool live() const { return live_; }
    MoveKind kind() const { return kind_; }
    int64_t t0() const { return t0_; }
    int64_t shift() const { return shift_; }
    int64_t swap_at() const { return t0_ + swap_; }
    int64_t end_at() const { return t0_ + end_; }
    bool reversed() const { return reversed_; }
    uint32_t serial() const { return serial_; }
    int64_t frame_time(int64_t at);
    int64_t shown(int64_t at);
    bool clocked() const { return clock_; }
    fx at(Part p, Prop q, int64_t t) const;
    bool has(Part p, Prop q) const { return leg(p, q) != nullptr; }
    const Leg* leg(Part p, Prop q) const;
    const Leg* last(Part p, Prop q) const;
    int legs_of(Part p, Prop q) const;
    bool span(Part p, Prop q, fx& from, fx& to) const;
    bool moving(Part p, int64_t t) const;
    int64_t part_end(Part p) const;
    void reverse(int64_t now);
    void shift(Part p, int ms, int64_t from = INT64_MIN);
    void hold(Part p) { held_ |= part_bit(p); }
    void hold(uint64_t group) { held_ |= group; }
    bool held(Part p) const { return held_ >> (int)p & 1; }
    int release(Part p, int64_t at);
    int release(uint64_t group, int64_t at);
    int release_words(int64_t at);
    bool cut() const { return cut_; }
    int word_end() const { return word_end_; }
    int word_legs(WordLeg k, const MoveEnds& e, int delay = 0);
    void np_shift(int ms);
    int np_release(int64_t at);
    void np_pop(int delay);
    static int np_ink(NpLeave l = NpLeave::Pane);
    static int pop_anchor(MoveKind k, int top, int h);
    void set(Part p, Prop q, fx v);
    void from(Part p, Prop q, fx v);
    void add(const Leg& l);
    void append(const Leg& l);
    void keys(Part p, Prop q, const Key* k, int n, Curve c = Curve::Linear, int delay = 0);
    void pivot(Part p, fx x, fx y, int at);
    void clear(Part p);
    void land(Part p);
    const std::vector<Leg>& legs() const { return legs_; }

private:
    void ramp(Part p, Prop q, fx from, fx to, int delay, int dur, Curve c);
    void step(Part p, Prop q, fx from, fx to, int delay);
    void erase(Part p, Prop q);
    void page_in(const MoveEnds& e, const MoveForms& f);
    void rows_in(const MoveEnds& e, const MoveForms& f);
    void pop_in(const MoveEnds& e, const MoveForms& f);
    void tie(Part p) { tied_ |= part_bit(p); }
    fx value(Part p, Prop q, int64_t e) const;
    const Leg* active(Part p, Prop q, int64_t e) const;
    void np_legs(MoveKind k, const MoveEnds& e);
    void np_group(int ink, const MoveEnds& e);
    void album_leave(const MoveEnds& e);
    void detail_leave(const MoveEnds& e);
    void t8_legs(const MoveEnds& e);
    void turn_legs(const MoveEnds& e);
    void finish();
    void col_legs(const MoveEnds& e, const MoveForms& f);
    void art_legs(const MoveEnds& e, const MoveForms& f);
    void art_back_legs(const MoveEnds& e, const MoveForms& f);
    void tile_back(const MoveEnds& e);
    void row_push(const MoveEnds& e, const MoveForms& f);
    void pivot_push(const MoveEnds& e, const MoveForms& f);
    void row_pop(const MoveEnds& e, const MoveForms& f);
    void pivot_pop(const MoveEnds& e, const MoveForms& f);
    void rows_out(const MoveEnds& e, bool home);
    void zoom_legs(const MoveEnds& e, const Key* s, int ns, const Key* a, int na, fx px, int header);
    void page_zoom(const MoveEnds& e);
    void page_fade(const MoveEnds& e);

    MoveKind kind_ = MoveKind::T3;
    int64_t t0_ = 0;
    int swap_ = 0, end_ = 0;
    int word_end_ = 0;
    uint64_t held_ = 0, tied_ = 0;
    uint32_t serial_ = 0;
    bool clock_ = false;
    int64_t first_ = INT64_MIN, shift_ = 0;
    bool live_ = false, reversed_ = false, morph_ = true, cut_ = false;
    struct Off {
        Part p;
        Prop q;
        int at;
        fx d;
    };
    std::vector<Leg> legs_;
    std::vector<Off> offs_;
    fx base_[(int)Part::Count][(int)Prop::Count] = {};
};

}
