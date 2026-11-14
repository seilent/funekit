#include "pagemove.h"

#include <algorithm>
#include <cstring>

#include "tokens.h"

namespace fd {

namespace {

int ms(int32_t t) { return t >> kFxBits; }

bool on(const char* mode, const char* want) { return std::strcmp(mode, want) == 0; }

ContentForm form_of(const char* mode)
{
    return on(mode, "step") ? ContentForm::Step : on(mode, "rest") ? ContentForm::Rest : ContentForm::Zoom;
}

constexpr int kTail = 17;
constexpr int kT2RowsInk = 17, kT2nPlugInk = 150;

constexpr fx F(double v) { return (fx)(v * 65536 + (v < 0 ? -0.5 : 0.5)); }

template <int N> constexpr int nkeys(const Key (&)[N]) { return N; }

constexpr Key kT2Scale[] = {{17, F(0.476)},  {50, F(0.548)},  {67, F(0.599)},  {83, F(0.646)},  {100, F(0.686)},
                            {117, F(0.724)}, {133, F(0.762)}, {150, F(0.791)}, {167, F(0.823)}, {183, F(0.845)},
                            {200, F(0.867)}, {233, F(0.910)}, {267, F(0.940)}, {300, F(0.965)}, {333, F(0.982)},
                            {367, F(0.994)}, {383, F(0.997)}, {400, F(1.0)}};
constexpr Key kT2Base[] = {{0, 0},           {17, F(0.158)},  {33, F(0.172)},  {50, F(0.219)},  {67, F(0.264)},
                           {83, F(0.314)},   {100, F(0.360)}, {117, F(0.408)}, {133, F(0.458)}, {150, F(0.503)},
                           {167, F(0.553)},  {183, F(0.597)}, {200, F(0.640)}, {233, F(0.726)}, {267, F(0.800)},
                           {300, F(0.868)},  {333, F(0.922)}, {367, F(0.963)}, {383, F(0.978)}, {400, F(0.989)},
                           {417, F(0.997)},  {434, F(1.0)}};

constexpr Key kT4hScale[] = {{17, F(0.93)},   {50, F(0.87)},   {67, F(0.84)},  {83, F(0.79)},
                             {117, F(0.62)},  {150, F(0.53)},  {167, F(0.47)}, {183, F(0.43)},
                             {200, F(0.385)}, {217, F(0.34)},  {233, F(0.31)}};
constexpr int kT4hScaleEnd = 300;
constexpr Key kT4hBase[] = {{0, 0},          {67, F(0.46)},  {100, F(0.58)}, {133, F(0.73)},
                            {167, F(0.85)},  {183, F(0.87)}, {200, F(0.90)}, {217, F(0.93)},
                            {233, F(0.96)},  {250, F(0.98)}, {300, F(1.0)}};
constexpr Key kT4hPitch[] = {{0, F(0.975)}, {17, F(0.94)}, {50, F(0.928)}, {67, F(0.912)}, {83, F(0.90)},
                             {100, F(0.885)}};
constexpr Key kT4hDx[] = {{0, F(2.6)},  {17, F(5.3)}, {33, F(6.0)},  {50, F(6.6)},
                          {67, F(8.6)}, {83, F(9.9)}, {100, F(11.2)}};

constexpr int kT3RowWordIn = 100, kT3RowWordEnd = 434;
constexpr fx kT3RowWordDrop = F(46.4);
constexpr Key kT3RowScale[] = {{17, F(0.235)},  {33, F(0.28)},   {50, F(0.325)},  {67, F(0.36)},   {83, F(0.405)},
                               {100, F(0.445)}, {117, F(0.49)},  {133, F(0.535)}, {150, F(0.575)}, {167, F(0.615)},
                               {183, F(0.65)},  {200, F(0.695)}, {217, F(0.725)}, {233, F(0.76)},  {250, F(0.80)},
                               {267, F(0.83)},  {283, F(0.86)},  {300, F(0.885)}, {317, F(0.91)},  {333, F(0.93)},
                               {350, F(0.95)},  {367, F(0.965)}, {383, F(0.985)}, {400, F(0.995)}, {434, F(1.0)}};
constexpr Key kT3RowBase[] = {{100, 0},          {117, F(0.117)}, {133, F(0.213)}, {150, F(0.311)}, {167, F(0.391)},
                              {183, F(0.471)},   {200, F(0.542)}, {233, F(0.668)}, {267, F(0.775)}, {300, F(0.853)},
                              {333, F(0.919)},   {350, F(0.944)}, {367, F(0.966)}, {400, F(0.989)}, {434, F(1.0)}};
constexpr Key kT3OldHeader[] = {{0, F(1.0)}, {17, F(1.10)}, {33, F(1.13)}, {50, F(1.215)}, {67, F(1.31)}};
constexpr fx kT3OldHeaderX = F(0.4), kT3OldRise = F(-55.8);
constexpr int kT3OldOut = 83;

constexpr Key kT3PivHeader[] = {{0, F(1.0)}, {17, F(1.07)}, {33, F(1.15)}, {50, F(1.25)}, {67, F(1.37)}};
constexpr Key kT3PivHeaderA[] = {{0, F(1.0)},  {17, F(0.86)},  {33, F(0.66)},  {50, F(0.52)},
                                 {67, F(0.45)}, {83, F(0.20)}, {100, F(0.10)}, {117, 0}};
constexpr fx kT3PivHeaderX = F(0.38), kT3PivRise = F(-74.1);
constexpr int kT3PivRiseEnd = 100;
constexpr Key kT3List[] = {{0, F(1.0)}, {117, F(1.11)}, {200, F(1.19)}};
constexpr Key kT3ListA[] = {{0, F(1.0)}, {33, F(0.94)}, {83, F(0.65)}, {150, F(0.35)}, {200, F(0.14)}, {233, 0}};
constexpr Key kT3Thumb[] = {{33, 0}, {100, F(0.4306)}, {200, F(0.7222)}, {300, F(0.9167)}, {383, F(1.0)}};
constexpr Key kT3PivRow[] = {{267, F(0.63)},  {283, F(0.68)},  {300, F(0.75)},  {317, F(0.82)}, {333, F(0.876)},
                             {350, F(0.924)}, {367, F(0.959)}, {383, F(0.979)}, {400, F(1.0)}};
constexpr int kT3PivRowIn = 267, kT3PivRowLand = 400, kT3PivContent = 400;
constexpr fx kT3PivRowA0 = F(0.20), kT3PivRowX = F(0.59), kT3PivRowY = F(0.46);

constexpr Key kT4RowDy[] = {{0, F(7.9)},   {33, F(21.8)}, {50, F(27.8)}, {67, F(33.7)},
                            {83, F(39.0)}, {100, F(43.7)}, {117, F(47.6)}};
constexpr Key kT4RowPitch[] = {{0, F(0.961)},  {33, F(0.942)}, {50, F(0.930)}, {67, F(0.914)},
                               {83, F(0.903)}, {100, F(0.891)}, {117, F(0.883)}};
constexpr Key kT4RowDx[] = {{0, F(3.3)}, {33, F(7.9)}, {67, F(11.2)}, {100, F(13.9)}};
constexpr Key kT4RowA[] = {{0, F(1.0)},   {33, F(0.91)},  {50, F(0.75)},  {67, F(0.61)},
                           {83, F(0.50)},  {100, F(0.39)}, {117, F(0.27)}, {133, F(0.15)},
                           {150, F(0.09)}, {167, F(0.03)}, {183, 0}};
constexpr Key kT4RowWord[] = {{67, F(6.3)},   {83, F(5.5)},   {100, F(4.7)},  {117, F(4.0)},  {133, F(3.4)},
                              {150, F(2.8)},  {167, F(2.36)}, {183, F(2.03)}, {200, F(1.73)}, {217, F(1.50)},
                              {233, F(1.31)}, {250, F(1.19)}, {267, F(1.09)}, {283, F(1.03)}, {300, F(1.0)}};
constexpr int kT4RowWordOut = 316;
constexpr Key kT4RowZoom[] = {{200, F(1.18)},  {217, F(1.115)}, {233, F(1.088)},
                              {250, F(1.04)},  {267, F(1.007)}, {283, F(1.0)}};
constexpr Key kT4RowZoomA[] = {{200, 0},         {217, F(0.04)}, {233, F(0.19)},
                               {250, F(0.46)},   {267, F(0.79)}, {283, F(0.95)}};
constexpr fx kT4RowZoomX = F(0.36);
constexpr int kT4RowHeader = 283, kT4RowIn = 300, kT4RowInLen = 167;

constexpr Key kT4PivLeave[] = {{0, F(1.0)},   {17, F(0.97)},  {33, F(0.87)},  {50, F(0.68)},  {67, F(0.60)},
                               {83, F(0.48)},  {100, F(0.35)}, {117, F(0.23)}, {133, F(0.15)}, {150, F(0.09)},
                               {167, F(0.06)}, {183, F(0.05)}, {200, 0}};
constexpr Key kT4PivWordA[] = {{0, F(1.0)},   {17, F(0.97)},  {33, F(0.87)},  {50, F(0.68)},
                               {67, F(0.60)},  {83, F(0.48)},  {100, F(0.35)}, {117, F(0.23)},
                               {133, F(0.15)}, {150, F(0.09)}, {167, F(0.06)}, {183, 0}};
constexpr int kT4PivWordOut = 183;
constexpr Key kT4PivPivotsA[] = {{17, F(1.0)},  {33, F(0.63)}, {50, F(0.55)}, {67, F(0.43)},
                                 {83, F(0.26)}, {100, F(0.15)}, {117, 0}};
constexpr fx kT4PivPivotsDx = F(19.9), kT4PivPivotsDy = F(26.5);
constexpr int kT4PivPivotsMove = 50;
constexpr Key kT4PivZoomA[] = {{183, F(0.25)}, {200, F(0.34)}, {217, F(0.43)}, {233, F(0.53)},
                               {250, F(0.65)}, {267, F(0.76)}, {283, F(0.92)}, {300, F(1.0)}};
constexpr fx kT4PivZoomX = F(0.4);
constexpr int kT4PivZoomIn = 183, kT4PivHeader = 300;
constexpr Key kT4PivPivots[] = {{300, 0}, {317, F(0.02)}, {333, F(0.79)}, {350, F(1.0)}};
constexpr Key kT4PivRows[] = {{333, F(0.16)}, {350, F(0.30)}, {367, F(0.41)}, {383, F(0.54)},
                              {400, F(0.67)}, {417, F(0.77)}, {433, F(0.83)}, {450, F(0.88)},
                              {467, F(0.92)}, {483, F(0.95)}, {500, F(0.99)}, {516, F(1.0)}};

constexpr int kT3aIn = 83, kT3aLand = 450;
constexpr fx kT3aRide = F(0.85);
constexpr Key kT4aZoom[] = {{150, F(1.35)}, {200, F(1.16)}, {233, F(1.07)}, {267, F(1.01)}, {283, F(1.0)}};
constexpr Key kT4aZoomA[] = {{133, F(0.22)}, {150, F(0.53)}, {167, F(0.68)}, {183, F(0.77)},
                             {200, F(0.82)}, {217, F(0.91)}, {233, F(1.0)}};
constexpr fx kT4aZoomX = F(0.33);
constexpr int kT4aHeader = 283;
constexpr Key kT4aCut[] = {{267, 0}, {283, F(0.5)}, {300, F(1.0)}};

constexpr Key kT3zScale[] = {{50, F(0.45)},   {100, F(0.53)},  {150, F(0.632)}, {200, F(0.729)},
                             {250, F(0.823)}, {300, F(0.899)}, {350, F(0.96)},  {417, F(1.0)}};
constexpr Key kT3zA[] = {{33, F(0.04)},  {100, F(0.26)}, {150, F(0.54)}, {200, F(0.79)},
                         {250, F(0.90)}, {300, F(0.95)}, {350, F(0.98)}, {417, F(1.0)}};
constexpr Key kT3zX[] = {{100, F(0.69)}, {417, F(0.55)}};
constexpr fx kT3zY = F(0.84);
constexpr int kT3zCut = 17, kT3zLand = 417;
constexpr Key kT4zA[] = {{0, F(1.0)}, {17, F(0.45)}, {33, F(0.26)}, {50, F(0.11)}, {67, F(0.06)}, {83, 0}};
constexpr fx kT4zDrop = F(13.2), kT4zHeader0 = F(0.37);
constexpr int kT4zDropLen = 67, kT4zHeader = 133, kT4zHeaderLand = 300;
constexpr Key kT4zCut[] = {{283, 0}, {300, F(0.5)}, {317, F(1.0)}};

constexpr Key kT7PaneR[] = {{0, F(-2.96)}, {250, F(-68.29)}, {335, F(-85.93)}};
constexpr Key kT7PaneX[] = {{0, 0}, {250, F(68.51)}, {326, F(84.7)}};
constexpr Key kT7PanePX[] = {{0, F(-1.61)}, {250, F(-25.21)}, {293, F(-27.66)}};
constexpr int kT7PaneFade = 245, kT7PaneGone = 336;
constexpr Key kT7PeekX[] = {{0, 0},          {17, F(7.2)},   {33, F(19.8)},  {50, F(25.0)},  {67, F(29.6)},
                            {83, F(33.6)},   {100, F(35.6)}, {117, F(37.0)}, {150, F(34.3)}, {167, F(33.0)},
                            {183, F(29.6)},  {200, F(23.7)}, {217, F(15.8)}, {234, F(6.6)},  {284, F(-4.6)}};
constexpr Key kT7PeekY[] = {{0, 0},           {17, F(-0.7)},   {33, F(-2.6)},   {50, F(-4.0)},  {67, F(-5.3)},
                            {83, F(-6.6)},    {100, F(-7.9)},  {117, F(-8.6)},  {150, F(-11.3)}, {167, F(-11.9)},
                            {183, F(-13.2)},  {200, F(-14.6)}, {217, F(-15.3)}, {234, F(-16.6)}, {284, F(-17.9)}};
constexpr Key kT7PeekR[] = {{167, 0},        {183, F(25.84)}, {200, F(25.84)},
                            {217, F(36.87)}, {234, F(45.57)}, {284, F(53.13)}};
constexpr Key kT7PeekA[] = {{0, F(1.0)}, {17, F(0.5)}, {284, F(0.5)}, {317, 0}};
constexpr Key kT7Music[] = {{0, 0},          {17, F(-2.0)},   {33, F(-2.9)},   {50, F(-5.3)},   {67, F(-9.3)},
                            {83, F(-14.8)},  {100, F(-21.1)}, {117, F(-33.3)}, {133, F(-39.3)}, {150, F(-46.6)},
                            {167, F(-59.4)}, {183, F(-68.5)}, {200, F(-77.4)}};
constexpr Key kT7RingX[] = {{0, F(-15)},    {67, F(17.4)},  {100, F(23.2)}, {117, F(26.1)}, {134, F(28.0)},
                            {150, F(28.2)}, {167, F(27.7)}, {184, F(26.4)}, {200, F(23.5)}, {217, F(20.1)},
                            {234, F(17.6)}, {250, F(14.1)}, {267, F(11.2)}, {284, F(8.4)},  {300, F(6.2)},
                            {317, F(4.3)},  {334, F(2.8)},  {351, F(1.4)},  {367, F(0.6)}};
constexpr Key kT7RingY[] = {{0, F(21.5)},   {67, F(16.8)},  {100, F(14.9)}, {117, F(13.1)}, {134, F(11.3)},
                            {150, F(9.6)},  {167, F(7.8)},  {184, F(6.3)},  {200, F(5.0)},  {217, F(3.7)},
                            {234, F(2.8)},  {250, F(1.9)},  {267, F(1.3)},  {284, F(0.7)},  {317, F(0.4)},
                            {334, F(0.1)},  {367, 0}};
constexpr Key kT7RingS[] = {{67, F(0.89)}, {150, F(0.93)}, {367, F(1.0)}};
constexpr Key kT7RingR[] = {{0, F(44)},       {84, F(44)},      {100, F(37.81)}, {117, F(33.90)},
                            {134, F(28.36)},  {150, F(23.07)},  {167, F(11.48)}, {184, 0}};

constexpr int kT8OutFaint = 183, kT8OutFaintEnd = 200;
constexpr fx kT8OutFaintA = F(0.3);
constexpr Key kT8Turn[] = {{0, F(-78.46)},   {67, F(-57.32)},  {83, F(-50.95)},  {100, F(-44.77)}, {117, F(-40.54)},
                           {133, F(-33.90)}, {150, F(-31.79)}, {167, F(-27.13)}, {183, F(-23.07)}, {200, F(-21.57)},
                           {217, F(-18.19)}, {233, F(-14.07)}, {250, F(-8.11)},  {283, 0}};
constexpr Key kT8Head[] = {{0, F(0.13)},  {17, F(0.18)}, {33, F(0.42)},  {50, F(0.69)},  {67, F(0.82)},
                           {83, F(0.89)}, {100, F(0.92)}, {117, F(0.95)}, {250, F(1.0)}};
constexpr Key kT8Row0[] = {{0, F(0.07)},  {17, F(0.15)}, {33, F(0.30)},  {50, F(0.47)},  {67, F(0.74)},
                           {83, F(0.84)}, {100, F(0.87)}, {117, F(0.91)}, {250, F(1.0)}};
constexpr Key kT8Row1[] = {{17, F(0.12)}, {33, F(0.26)}, {50, F(0.40)},  {67, F(0.79)},
                           {83, F(0.88)}, {100, F(0.92)}, {117, F(0.96)}, {250, F(1.0)}};
constexpr Key kT8Row2[] = {{17, F(0.05)}, {33, F(0.14)}, {50, F(0.31)},  {67, F(0.57)}, {83, F(0.72)},
                           {100, F(0.82)}, {117, F(0.87)}, {133, F(0.93)}, {250, F(1.0)}};
constexpr Key kT8Row3[] = {{33, F(0.08)},  {50, F(0.20)},  {67, F(0.42)}, {83, F(0.60)},
                           {100, F(0.75)}, {117, F(0.80)}, {133, F(0.84)}, {250, F(1.0)}};
constexpr Key kT8Rows[] = {{50, F(0.19)},  {67, F(0.43)}, {83, F(0.52)}, {100, F(0.67)},
                           {117, F(0.75)}, {133, F(0.81)}, {250, F(1.0)}};
constexpr Key kT8Header[] = {{67, F(0.33)}, {83, F(0.63)}, {100, F(0.89)}, {250, F(1.0)}};
constexpr Key kT8Artist[] = {{50, F(0.19)}, {250, F(1.0)}};
constexpr Key kT8PaneLeft[] = {{0, F(26.9)},   {17, F(27.8)},  {67, F(27.8)}, {83, F(26.0)},  {117, F(22.5)},
                               {133, F(20.7)}, {150, F(18.1)}, {184, F(13.7)}, {217, F(11.0)}, {250, F(7.9)},
                               {284, F(4.0)},  {317, F(1.8)},  {351, 0}};
constexpr Key kT8PaneRight[] = {{0, F(-82.5)},  {17, F(-65.7)}, {33, F(-46.3)}, {50, F(-28.7)}, {67, F(-14.6)},
                                {83, F(-4.0)},  {117, F(7.5)},  {133, F(10.1)}, {150, F(11.9)}, {184, F(11.9)},
                                {217, F(11.0)}, {250, F(8.4)},  {284, F(4.9)},  {317, F(2.2)},  {351, F(0.4)},
                                {367, F(-0.4)}};
constexpr Key kT8PeekX[] = {{0, F(-22.1)},  {17, F(-7.1)},  {33, F(8.8)},   {50, F(21.2)},  {67, F(29.1)},
                            {83, F(34.4)},  {117, F(37.0)}, {133, F(42.3)}, {150, F(33.5)}, {184, F(27.3)},
                            {217, F(22.9)}, {250, F(15.9)}, {284, F(9.7)},  {317, F(5.3)},  {351, F(1.9)},
                            {367, F(0.6)},  {384, 0}};
constexpr Key kT8PeekY[] = {{0, F(-19.4)},  {17, F(-17.6)}, {33, F(-16.8)}, {50, F(-15.0)}, {67, F(-13.2)},
                            {83, F(-11.5)}, {117, F(-9.7)}, {133, F(-7.9)}, {150, F(-7.1)}, {184, F(-4.4)},
                            {217, F(-3.5)}, {250, F(-1.8)}, {284, F(-1.3)}, {317, F(-0.6)}, {351, 0}};
constexpr Key kT8PeekR[] = {{0, F(60.0)}, {17, F(53.13)}, {33, F(45.57)}, {50, F(36.87)}, {67, 0}};
constexpr Key kT8PeekA[] = {{0, F(0.26)}, {384, F(1.0)}};
constexpr fx kT8HomeR = F(-65.6), kT8HomeX = F(-3.46), kT8HomeHinge = F(5.9);
constexpr int kT8HomeTurn = 349, kT8HomeSlide = 138, kT8HomeFade = 50;

}

fx Leg::at(int64_t e) const
{
    if (dur <= 0)
        return e >= delay ? to : from;
    if (e <= delay)
        return from;
    if (e >= end())
        return to;
    fx p = (fx)((e - delay) * kOne / dur);
    return fx_lerp(from, to, fd::curve(curve, p));
}

MoveForms MoveForms::board()
{
    MoveForms f;
    f.leave_fade = on(tok::motion_leaveFade, "fade");
    f.content = form_of(tok::motion_contentZoom);
    f.chrome_slide = on(tok::motion_chromeSlide, "slide");
    f.header_zoom = on(tok::motion_headerForm, "zoom");
    f.t3m_fade = on(tok::motion_t3mForm, "fade");
    f.word_morph = on(tok::motion_wordForm, "morph");
    f.hub_rows = form_of(tok::motion_contentHubRows);
    f.page_rows = form_of(tok::motion_contentPageRows);
    return f;
}

const Leg* PageMove::leg(Part p, Prop q) const
{
    const Leg* f = nullptr;
    for (const Leg& l : legs_)
        if (l.part == p && l.prop == q && (!f || l.delay < f->delay))
            f = &l;
    return f;
}

const Leg* PageMove::last(Part p, Prop q) const
{
    const Leg* z = nullptr;
    for (const Leg& l : legs_)
        if (l.part == p && l.prop == q && (!z || l.delay >= z->delay))
            z = &l;
    return z;
}

int PageMove::legs_of(Part p, Prop q) const
{
    int n = 0;
    for (const Leg& l : legs_)
        n += l.part == p && l.prop == q;
    return n;
}

bool PageMove::span(Part p, Prop q, fx& from, fx& to) const
{
    const Leg* f = leg(p, q);
    const Leg* z = last(p, q);
    if (!f || !z)
        return false;
    from = f->from;
    to = z->to;
    return true;
}

const Leg* PageMove::active(Part p, Prop q, int64_t e) const
{
    const Leg* a = nullptr;
    for (const Leg& l : legs_)
        if (l.part == p && l.prop == q && l.delay <= e && (!a || l.delay >= a->delay))
            a = &l;
    return a ? a : leg(p, q);
}

fx PageMove::value(Part p, Prop q, int64_t e) const
{
    fx v = base_[(int)p][(int)q];
    for (const Off& o : offs_)
        if (o.p == p && o.q == q && e >= o.at)
            v += o.d;
    const Leg* f = leg(p, q);
    if (!f)
        return v;
    if (held(p)) {
        if (!(kWordGroup & part_bit(p)))
            return base_[(int)p][(int)q];
        e = std::min<int64_t>(e, (int64_t)f->delay - 1);
    } else if ((tied_ & part_bit(p)) && (held_ & kWordGroup)) {
        e = std::min<int64_t>(e, (int64_t)f->delay - 1);
    } else if (p == Part::Leave && held(Part::Content)) {
        const Leg* z = last(p, q);
        e = std::min<int64_t>(e, z->dur > kTail ? z->end() - kTail : (int64_t)z->delay - 1);
    }
    return v - base_[(int)p][(int)q] + active(p, q, e)->at(e);
}

fx PageMove::at(Part p, Prop q, int64_t t) const { return value(p, q, t - t0_); }

bool PageMove::moving(Part p, int64_t t) const
{
    int64_t e = t - t0_;
    for (const Leg& l : legs_)
        if (l.part == p && l.dur > 0 && e > l.delay && e < l.end())
            return true;
    return false;
}

int64_t PageMove::part_end(Part p) const
{
    int64_t end = 0;
    for (const Leg& l : legs_)
        if (l.part == p)
            end = std::max(end, l.end());
    return t0_ + end;
}

void PageMove::set(Part p, Prop q, fx v) { base_[(int)p][(int)q] = v; }

void PageMove::from(Part p, Prop q, fx v)
{
    Leg* f = nullptr;
    for (Leg& l : legs_)
        if (l.part == p && l.prop == q && (!f || l.delay < f->delay))
            f = &l;
    if (f)
        f->from = v;
    else
        set(p, q, v);
}

void PageMove::erase(Part p, Prop q)
{
    legs_.erase(std::remove_if(legs_.begin(), legs_.end(),
                               [&](const Leg& l) { return l.part == p && l.prop == q; }),
                legs_.end());
}

void PageMove::clear(Part p)
{
    legs_.erase(std::remove_if(legs_.begin(), legs_.end(), [&](const Leg& l) { return l.part == p; }), legs_.end());
}

void PageMove::add(const Leg& l)
{
    erase(l.part, l.prop);
    legs_.push_back(l);
}

void PageMove::append(const Leg& l) { legs_.push_back(l); }

void PageMove::keys(Part p, Prop q, const Key* k, int n, Curve c, int delay)
{
    erase(p, q);
    if (n <= 0)
        return;
    if (n == 1) {
        legs_.push_back(Leg{p, q, k[0].v, k[0].v, delay + k[0].t, 0, Curve::Linear});
        return;
    }
    for (int i = 0; i + 1 < n; i++)
        legs_.push_back(Leg{p, q, k[i].v, k[i + 1].v, delay + k[i].t, std::max(0, k[i + 1].t - k[i].t), c});
}

void PageMove::pivot(Part p, fx x, fx y, int at)
{
    fx s = value(p, Prop::S, at), px0 = value(p, Prop::PX, at), py0 = value(p, Prop::PY, at);
    offs_.push_back({p, Prop::X, at, fx_mul(kOne - s, px0 - x)});
    offs_.push_back({p, Prop::Y, at, fx_mul(kOne - s, py0 - y)});
    append(Leg{p, Prop::PX, px0, x, at, 0, Curve::Linear});
    append(Leg{p, Prop::PY, py0, y, at, 0, Curve::Linear});
    finish();
}

void PageMove::ramp(Part p, Prop q, fx from, fx to, int delay, int dur, Curve c)
{
    add(Leg{p, q, from, to, delay, std::max(dur, 0), c});
}

void PageMove::step(Part p, Prop q, fx from, fx to, int delay) { add(Leg{p, q, from, to, delay, 0, Curve::Linear}); }

void PageMove::finish()
{
    int end = std::max(end_, swap_);
    for (const Leg& l : legs_)
        end = std::max(end, (int)l.end());
    end_ = end;
}

int PageMove::word_legs(WordLeg k, const MoveEnds& e, int delay)
{
    auto box = [&](Part p, fx s0, fx s1, fx x0, fx x1, fx y0, fx y1, int sd, int sl, int xd, int xl, int yd, int yl,
                   Curve sc, Curve xc, Curve yc) {
        if (!morph_)
            return;
        ramp(p, Prop::S, s0, s1, delay + sd, sl, sc);
        ramp(p, Prop::X, x0, x1, delay + xd, xl, xc);
        ramp(p, Prop::Y, y0, y1, delay + yd, yl, yc);
    };
    auto word = [&](int sd, int sl, int xd, int xl, int yd, int yl, Curve sc, Curve xc, Curve yc, int out) {
        box(Part::Word, e.word_s0, e.word_s1, e.word_x0, e.word_x1, e.word_y0, e.word_y1, sd, sl, xd, xl, yd, yl, sc, xc,
            yc);
        step(Part::Word, Prop::A, kOne, 0, delay + out);
    };
    auto lerp = [](fx a, fx b, fx p) { return a + fx_mul(b - a, p); };
    auto chain = [&](Prop q, fx v0, fx v1, const Key* k, int n, bool progress, int from) {
        std::vector<Key> ks;
        ks.reserve((size_t)n + 1);
        if (from >= 0)
            ks.push_back({from, v0});
        for (int i = 0; i < n; i++)
            ks.push_back({k[i].t, progress ? lerp(v0, v1, k[i].v) : k[i].v});
        keys(Part::Word, q, ks.data(), (int)ks.size(), Curve::Linear, delay);
    };
    int t4md = ms(tok::motion_t4mDelay);
    switch (k) {
    case WordLeg::HomeOpen: {
        int wl = ms(tok::motion_t2Word), bd = ms(tok::motion_t2BaseDelay), bl = ms(tok::motion_t2Base);
        int end = std::max(wl, bd + bl);
        if (morph_) {
            chain(Prop::S, e.word_s0, e.word_s1, kT2Scale, nkeys(kT2Scale), false, 0);
            ramp(Part::Word, Prop::X, e.word_x0, e.word_x1, delay + bd, bl, Curve::EaseOut);
            chain(Prop::Y, e.word_y0, e.word_y1, kT2Base, nkeys(kT2Base), true, -1);
        }
        step(Part::Word, Prop::A, kOne, 0, delay + end);
        return delay + end;
    }
    case WordLeg::HomeBack: {
        int bl = ms(tok::motion_t4hBox);
        if (morph_) {
            std::vector<Key> ks;
            ks.push_back({0, e.word_s0});
            ks.insert(ks.end(), kT4hScale, kT4hScale + nkeys(kT4hScale));
            ks.push_back({kT4hScaleEnd, e.word_s1});
            keys(Part::Word, Prop::S, ks.data(), (int)ks.size(), Curve::Linear, delay);
            ramp(Part::Word, Prop::X, e.word_x0, e.word_x1, delay, bl, Curve::PowOut15);
            chain(Prop::Y, e.word_y0, e.word_y1, kT4hBase, nkeys(kT4hBase), true, -1);
        }
        step(Part::Word, Prop::A, kOne, 0, delay + ms(tok::motion_t4hSwap) + ms(tok::motion_t4hItems));
        return delay + std::max(kT4hScaleEnd, bl);
    }
    case WordLeg::Push:
        if (e.morph_kind == MorphKind::Row) {
            if (morph_) {
                chain(Prop::S, e.morph_s0, e.morph_s1, kT3RowScale, nkeys(kT3RowScale), false, 0);
                ramp(Part::Word, Prop::X, e.morph_x0, e.morph_x1, delay, kT3RowWordIn, Curve::EaseOut);
                fx mid = e.morph_y1 + fx_mul(kT3RowWordDrop, e.px_u);
                ramp(Part::Word, Prop::Y, e.morph_y0, mid, delay, kT3RowWordIn, Curve::EaseOut);
                for (int i = 0; i + 1 < nkeys(kT3RowBase); i++)
                    append(Leg{Part::Word, Prop::Y, lerp(mid, e.morph_y1, kT3RowBase[i].v),
                               lerp(mid, e.morph_y1, kT3RowBase[i + 1].v), delay + kT3RowBase[i].t,
                               kT3RowBase[i + 1].t - kT3RowBase[i].t, Curve::Linear});
            }
            set(Part::Word, Prop::A, kOne);
            step(Part::Word, Prop::A, kOne, 0, delay + kT3RowWordEnd);
            return delay + kT3RowWordEnd;
        } else {
            int l = ms(tok::motion_morphPush);
            if (morph_) {
                ramp(Part::Word, Prop::S, e.morph_s0, e.morph_s1, delay + t4md, l, Curve::CubicOut);
                ramp(Part::Word, Prop::X, e.morph_x0, e.morph_x1, delay + t4md, l, Curve::CubicOut);
                ramp(Part::Word, Prop::Y, e.morph_y0, e.morph_y1, delay + t4md, l, Curve::CubicOut);
            }
            step(Part::Word, Prop::A, kOne, 0, delay + t4md + l);
            return delay + t4md + l;
        }
    case WordLeg::Pop:
        if (e.morph_kind == MorphKind::Row) {
            if (morph_) {
                fx s0 = e.morph_s0, s1 = e.morph_s1, ds = s0 - s1;
                std::vector<Key> ss, xs, ys;
                ss.push_back({0, s0});
                xs.push_back({0, e.morph_x0});
                ys.push_back({0, e.morph_y0});
                for (const Key& w : kT4RowWord) {
                    fx s = fx_mul(w.v, s1), p = ds != 0 ? fx_clamp(fx_div(s0 - s, ds), 0, kOne) : kOne;
                    ss.push_back({w.t, s});
                    xs.push_back({w.t, lerp(e.morph_x0, e.morph_x1, p)});
                    ys.push_back({w.t, lerp(e.morph_y0, e.morph_y1, p)});
                }
                keys(Part::Word, Prop::S, ss.data(), (int)ss.size(), Curve::Linear, delay);
                keys(Part::Word, Prop::X, xs.data(), (int)xs.size(), Curve::Linear, delay);
                keys(Part::Word, Prop::Y, ys.data(), (int)ys.size(), Curve::Linear, delay);
                Leg* first[3] = {};
                for (Leg& l : legs_)
                    if (l.part == Part::Word && l.delay == delay && l.prop != Prop::A)
                        first[l.prop == Prop::S ? 0 : l.prop == Prop::X ? 1 : 2] = &l;
                for (Leg* l : first)
                    if (l)
                        l->curve = Curve::EaseOut;
            }
            set(Part::Word, Prop::A, kOne);
            step(Part::Word, Prop::A, kOne, 0, delay + kT4RowWordOut);
            return delay + kT4RowWordOut;
        } else {
            int l = ms(tok::motion_morphPop);
            if (morph_) {
                ramp(Part::Word, Prop::S, e.morph_s0, e.morph_s1, delay + t4md, l, Curve::CubicOut);
                ramp(Part::Word, Prop::X, e.morph_x0, e.morph_x1, delay + t4md, l, Curve::CubicOut);
                ramp(Part::Word, Prop::Y, e.morph_y0, e.morph_y1, delay + t4md, l, Curve::CubicOut);
            }
            keys(Part::Word, Prop::A, kT4PivWordA, nkeys(kT4PivWordA), Curve::Linear, delay);
            return delay + kT4PivWordOut;
        }
    case WordLeg::Grow: {
        int ld = ms(tok::motion_t3mLabelDelay), ll = ms(tok::motion_t3mLabel);
        int bd = ms(tok::motion_t3mBaseDelay), bl = ms(tok::motion_t3mBase);
        int end = std::max(ld + ll, bd + bl);
        word(ld, ll, ld, ll, bd, bl, Curve::EaseOut, Curve::EaseOut, Curve::CubicOut, end);
        return delay + end;
    }
    case WordLeg::Shrink: {
        int l = ms(tok::motion_t4m);
        word(t4md, l, t4md, l, t4md, l, Curve::CubicOut, Curve::CubicOut, Curve::CubicOut, t4md + l);
        return delay + t4md + l;
    }
    case WordLeg::Hold: {
        set(Part::Word, Prop::S, e.word_s0);
        set(Part::Word, Prop::X, e.word_x0);
        set(Part::Word, Prop::Y, e.word_y0);
        set(Part::Word, Prop::A, kOne);
        int end = std::max(end_, swap_);
        for (const Leg& l : legs_)
            end = std::max(end, (int)l.end());
        return end;
    }
    }
    return delay;
}

void PageMove::rows_in(const MoveEnds& e, const MoveForms& f)
{
    bool t2 = kind_ == MoveKind::T2 || kind_ == MoveKind::T2n;
    clear(Part::Content);
    set(Part::Content, Prop::S, kOne);
    if (f.content == ContentForm::Rest) {
        step(Part::Content, Prop::A, 0, kOne, 0);
        step(Part::Leave, Prop::A, kOne, 0, 0);
    } else {
        int rl = ms(tok::motion_rows);
        ramp(Part::Content, Prop::S, tok::motion_rowsDy, kOne, 0, rl, Curve::PowOut15);
        ramp(Part::Content, Prop::X, e.rows_dx, 0, 0, rl, Curve::PowOut15);
        if (f.content == ContentForm::Step) {
            step(Part::Content, Prop::A, 0, kOne, 0);
        } else if (t2) {
            int ink = e.plug ? kT2nPlugInk : kT2RowsInk;
            step(Part::Content, Prop::A, 0, tok::motion_rowsT2Alpha, ink);
            append(Leg{Part::Content, Prop::A, tok::motion_rowsT2Alpha, kOne, ink, ms(tok::motion_rowsT2Fade),
                       Curve::Linear});
        } else {
            ramp(Part::Content, Prop::A, 0, kOne, 0, ms(tok::motion_rowsT3Fade), Curve::SmoothStep);
        }
    }
    if (t2) {
        erase(Part::Ground, Prop::A);
        std::vector<Leg> a;
        for (const Leg& l : legs_)
            if (l.part == Part::Content && l.prop == Prop::A)
                a.push_back(l);
        for (Leg g : a) {
            g.part = Part::Ground;
            append(g);
        }
        hold(Part::Ground);
    }
}

void PageMove::pop_in(const MoveEnds& e, const MoveForms& f)
{
    int after = ms(tok::motion_contentAfter), from = ms(tok::motion_leave);
    int word = word_end_ > 0 ? word_end_ : ms(tok::motion_headerDelay) + ms(tok::motion_header);
    int end = std::max(word + after, from + after);
    clear(Part::Content);
    if (kind_ == MoveKind::T4m || kind_ == MoveKind::T4a) {
        set(Part::Content, Prop::S, kOne);
        if (e.col_back)
            ramp(Part::Content, Prop::A, 0, kOne, ms(tok::motion_t3cBackDelay), ms(tok::motion_t3cBack), Curve::Linear);
        else if (f.content == ContentForm::Rest || f.content == ContentForm::Step)
            step(Part::Content, Prop::A, 0, kOne, word);
        else
            ramp(Part::Content, Prop::A, 0, kOne, word, ms(tok::motion_t4mFade), Curve::Linear);
        return;
    }
    if (f.content == ContentForm::Zoom)
        add(Leg{Part::Content, Prop::A, 0, kOne, from, end - from, Curve::Linear, after});
    else
        step(Part::Content, Prop::A, 0, kOne, from);
    if (!e.rows && f.content != ContentForm::Rest)
        add(Leg{Part::Content, Prop::S, tok::motion_contentScale, kOne, from, end - from, Curve::EaseOut, after});
    else
        set(Part::Content, Prop::S, kOne);
}

int PageMove::np_ink(NpForm form, const MoveForms& f)
{
    switch (form) {
    case NpForm::Front:
        return ms(tok::motion_t7PaneInk);
    case NpForm::Ring:
        return f.leave_fade ? ms(tok::motion_t7RingLeave) - ms(tok::motion_t7RingOverlap) : 0;
    case NpForm::Page:
        break;
    }
    return ms(tok::motion_t7LeaveHeader) + ms(tok::motion_npDark);
}

void PageMove::np_group(int ink, const MoveEnds& e)
{
    auto px = [&](const Key* k, int n, std::vector<Key>& out) {
        out.clear();
        for (int i = 0; i < n; i++)
            out.push_back({k[i].t, fx_mul(k[i].v, e.px_u)});
    };
    int fade = ms(tok::motion_t7Fade);
    int b = ink - ms(tok::motion_t7PaneInk);
    auto hold = [&](Prop q, fx v, int from, int32_t held, fx end, int32_t land, Curve c) {
        add(Leg{Part::NpIn, q, v, v, b + from, ms(held) - from, Curve::Linear});
        append(Leg{Part::NpIn, q, v, end, b + ms(held), ms(land) - ms(held), c});
    };
    hold(Prop::R, tok::motion_t7PlaneAngle, ms(tok::motion_t7PaneInk), tok::motion_t7PlaneHold, 0,
         tok::motion_t7PlaneLand, Curve::CubicOut);
    hold(Prop::X, fx_mul(tok::motion_t7SlideX, e.px_u), 0, tok::motion_t7SlideHold, 0, tok::motion_t7SlideLand,
         Curve::ExpOut6);
    hold(Prop::PX, fx_mul(tok::motion_t7HingeFrom, e.px_u), 0, tok::motion_t7HingeHold,
         fx_mul(tok::motion_t7HingeTo, e.px_u), tok::motion_t7HingeLand, Curve::EaseIn);
    ramp(Part::NpIn, Prop::A, 0, kOne, ink, fade, Curve::EaseOut);
    struct Ramp {
        Part p;
        int32_t start, land;
    };
    const Ramp rs[] = {{Part::NpArtist, tok::motion_t7PartArtist, tok::motion_t7LandArtist},
                       {Part::NpAlbum, tok::motion_t7PartAlbum, tok::motion_t7LandAlbum},
                       {Part::NpTitle, tok::motion_t7PartTitle, tok::motion_t7LandTitle},
                       {Part::NpRings, tok::motion_t7PartRings, tok::motion_t7LandRings},
                       {Part::NpTimeR, tok::motion_t7PartTimeR, tok::motion_t7LandTimeR},
                       {Part::NpBar, tok::motion_t7PartTimeR, tok::motion_t7LandTimeR},
                       {Part::NpTimeL, tok::motion_t7PartTimeL, tok::motion_t7LandTimeL},
                       {Part::NpShuffle, tok::motion_t7PartShuffle, tok::motion_t7LandShuffle},
                       {Part::NpNext, tok::motion_t7PartNext, tok::motion_t7LandNext}};
    for (const Ramp& r : rs)
        ramp(r.p, Prop::A, 0, kOne, ink + ms(r.start), ms(r.land) - ms(r.start), Curve::EaseOut);
    step(Part::NpStatus, Prop::A, 0, kOne, ink + ms(tok::motion_t7Status));
    std::vector<Key> k;
    px(kT7RingX, nkeys(kT7RingX), k);
    keys(Part::NpRing, Prop::X, k.data(), (int)k.size(), Curve::Linear, ink);
    px(kT7RingY, nkeys(kT7RingY), k);
    keys(Part::NpRing, Prop::Y, k.data(), (int)k.size(), Curve::Linear, ink);
    keys(Part::NpRing, Prop::S, kT7RingS, nkeys(kT7RingS), Curve::Linear, ink);
    keys(Part::NpRing, Prop::R, kT7RingR, nkeys(kT7RingR), Curve::Linear, ink);
    ramp(Part::NpRing, Prop::A, 0, kOne, ink, fade, Curve::EaseOut);
    step(Part::NpPhoto, Prop::A, 0, kOne, ink + ms(tok::motion_t7Rest));
}

void PageMove::t8_legs(const MoveEnds& e)
{
    auto px = [&](const Key* k, int n, std::vector<Key>& out) {
        out.clear();
        for (int i = 0; i < n; i++)
            out.push_back({k[i].t, fx_mul(k[i].v, e.px_u)});
    };
    int out = ms(tok::motion_t8OutEnd), in = out + ms(tok::motion_npDark);
    ramp(Part::NpOut, Prop::R, 0, tok::motion_t8OutAngle, ms(tok::motion_t8OutDelay), ms(tok::motion_t8Out),
         Curve::PowIn15);
    ramp(Part::NpOut, Prop::A, kOne, kT8OutFaintA, kT8OutFaint, kT8OutFaintEnd - kT8OutFaint, Curve::Linear);
    append(Leg{Part::NpOut, Prop::A, kT8OutFaintA, 0, out, 0, Curve::Linear});
    if (e.np_cut) {
        for (Part p : {Part::NpUnder, Part::NpUHeader, Part::NpURow0, Part::NpURow1, Part::NpURow2, Part::NpURow3,
                       Part::NpURows, Part::NpUArtist})
            step(p, Prop::A, 0, kOne, in);
        return;
    }
    std::vector<Key> k;
    if (e.np_form == NpForm::Front) {
        px(kT8PaneLeft, nkeys(kT8PaneLeft), k);
        keys(Part::NpUnder, Prop::X, k.data(), (int)k.size(), Curve::Linear, in);
        px(kT8PaneRight, nkeys(kT8PaneRight), k);
        keys(Part::NpEdge, Prop::X, k.data(), (int)k.size(), Curve::Linear, in);
        ramp(Part::NpUnder, Prop::A, 0, kOne, in, ms(tok::motion_t8InFade), Curve::Linear);
        px(kT8PeekX, nkeys(kT8PeekX), k);
        keys(Part::NpPeek, Prop::X, k.data(), (int)k.size(), Curve::Linear, in);
        px(kT8PeekY, nkeys(kT8PeekY), k);
        keys(Part::NpPeek, Prop::Y, k.data(), (int)k.size(), Curve::Linear, in);
        keys(Part::NpPeek, Prop::R, kT8PeekR, nkeys(kT8PeekR), Curve::Linear, in);
        keys(Part::NpPeek, Prop::A, kT8PeekA, nkeys(kT8PeekA), Curve::Linear, in);
        return;
    }
    if (e.np_home) {
        ramp(Part::NpUnder, Prop::R, kT8HomeR, 0, in, kT8HomeTurn, Curve::EaseOut);
        ramp(Part::NpUnder, Prop::X, fx_mul(kT8HomeX, e.px_u), 0, in, kT8HomeSlide, Curve::Linear);
        set(Part::NpUnder, Prop::PX, fx_mul(kT8HomeHinge, e.px_u));
        ramp(Part::NpUnder, Prop::A, 0, kOne, in, kT8HomeFade, Curve::Linear);
        return;
    }
    keys(Part::NpUnder, Prop::R, kT8Turn, nkeys(kT8Turn), Curve::Linear, in);
    struct Row {
        Part p;
        const Key* k;
        int n;
    };
    const Row rows[] = {{Part::NpUnder, kT8Head, nkeys(kT8Head)}, {Part::NpUHeader, kT8Header, nkeys(kT8Header)},
                        {Part::NpURow0, kT8Row0, nkeys(kT8Row0)}, {Part::NpURow1, kT8Row1, nkeys(kT8Row1)},
                        {Part::NpURow2, kT8Row2, nkeys(kT8Row2)}, {Part::NpURow3, kT8Row3, nkeys(kT8Row3)},
                        {Part::NpURows, kT8Rows, nkeys(kT8Rows)}, {Part::NpUArtist, kT8Artist, nkeys(kT8Artist)}};
    for (const Row& r : rows) {
        step(r.p, Prop::A, 0, r.k[0].v, in + r.k[0].t);
        for (int i = 0; i + 1 < r.n; i++)
            append(Leg{r.p, Prop::A, r.k[i].v, r.k[i + 1].v, in + r.k[i].t, r.k[i + 1].t - r.k[i].t, Curve::Linear});
    }
}

void PageMove::np_legs(MoveKind k, const MoveEnds& e, const MoveForms& f)
{
    if (k == MoveKind::T8) {
        t8_legs(e);
        return;
    }
    auto px = [&](const Key* ks, int n, std::vector<Key>& out) {
        out.clear();
        for (int i = 0; i < n; i++)
            out.push_back({ks[i].t, fx_mul(ks[i].v, e.px_u)});
    };
    std::vector<Key> ks;
    switch (e.np_form) {
    case NpForm::Page:
        if (f.leave_fade) {
            ramp(Part::NpLeave, Prop::A, kOne, 0, 0, ms(tok::motion_t7Leave), Curve::Linear);
            step(Part::NpLeaveHeader, Prop::A, kOne, 0, ms(tok::motion_t7LeaveHeader));
        } else {
            step(Part::NpLeave, Prop::A, kOne, 0, 0);
            step(Part::NpLeaveHeader, Prop::A, kOne, 0, 0);
        }
        break;
    case NpForm::Front:
        keys(Part::NpLeave, Prop::R, kT7PaneR, nkeys(kT7PaneR));
        px(kT7PaneX, nkeys(kT7PaneX), ks);
        keys(Part::NpLeave, Prop::X, ks.data(), (int)ks.size());
        px(kT7PanePX, nkeys(kT7PanePX), ks);
        keys(Part::NpLeave, Prop::PX, ks.data(), (int)ks.size());
        ramp(Part::NpLeave, Prop::A, kOne, 0, kT7PaneFade, kT7PaneGone - kT7PaneFade, Curve::EaseIn);
        append(Leg{Part::NpLeave, Prop::A, 0, 0, ms(tok::motion_t7PaneEnd), 0, Curve::Linear});
        px(kT7PeekX, nkeys(kT7PeekX), ks);
        keys(Part::NpPeek, Prop::X, ks.data(), (int)ks.size());
        px(kT7PeekY, nkeys(kT7PeekY), ks);
        keys(Part::NpPeek, Prop::Y, ks.data(), (int)ks.size());
        keys(Part::NpPeek, Prop::R, kT7PeekR, nkeys(kT7PeekR));
        keys(Part::NpPeek, Prop::A, kT7PeekA, nkeys(kT7PeekA));
        break;
    case NpForm::Ring:
        if (f.leave_fade) {
            int rl = ms(tok::motion_t7RingLeave);
            px(kT7Music, nkeys(kT7Music), ks);
            for (Part p : {Part::NpLeave, Part::NpMusic}) {
                keys(p, Prop::X, ks.data(), (int)ks.size());
                ramp(p, Prop::R, 0, kOne, 0, rl, Curve::EaseIn);
                ramp(p, Prop::A, kOne, 0, 0, rl, Curve::Linear);
            }
            ramp(Part::NpPeek, Prop::A, kOne, 0, 0, rl, Curve::Linear);
        } else {
            step(Part::NpLeave, Prop::A, kOne, 0, 0);
            step(Part::NpPeek, Prop::A, kOne, 0, 0);
            step(Part::NpMusic, Prop::A, kOne, 0, 0);
        }
        break;
    }
    int ink = np_ink(e.np_form, f);
    if (e.np_cut) {
        for (Part p : {Part::NpIn, Part::NpRing, Part::NpArtist, Part::NpAlbum, Part::NpTitle, Part::NpRings,
                       Part::NpTimeR, Part::NpTimeL, Part::NpBar, Part::NpShuffle, Part::NpNext, Part::NpStatus,
                       Part::NpPhoto})
            step(p, Prop::A, 0, kOne, ink);
        return;
    }
    np_group(ink, e);
}

void PageMove::page_in(const MoveEnds& e, const MoveForms& f)
{
    int hd = ms(tok::motion_headerDelay), hl = ms(tok::motion_header);
    if (!e.zoom) {
        step(Part::Header, Prop::A, 0, kOne, hd);
    } else if (!f.header_zoom) {
        step(Part::Header, Prop::A, 0, kOne, hd + hl);
    } else {
        ramp(Part::Zoom, Prop::S, tok::motion_headerZoom, kOne, hd, hl, Curve::EaseOut);
        step(Part::Zoom, Prop::A, 0, kOne, hd);
        step(Part::Header, Prop::A, 0, kOne, hd + hl);
    }
    set(Part::Zoom, Prop::PX, e.zoom_x);
    set(Part::Zoom, Prop::PY, e.zoom_y);
    if (e.pivots) {
        int pd = ms(tok::motion_pivotDelay), pl = ms(tok::motion_pivotIn), pf = ms(tok::motion_pivotFade);
        if (f.chrome_slide) {
            ramp(Part::Pivots, Prop::X, e.pivot_x, 0, pd, pl, Curve::EaseOut);
            ramp(Part::Pivots, Prop::Y, e.pivot_y, 0, pd, pl, Curve::EaseOut);
            ramp(Part::Pivots, Prop::A, 0, kOne, pd, pf, Curve::Linear);
        } else {
            step(Part::Pivots, Prop::A, 0, kOne, pd + pl);
        }
    }
    if (e.grounds)
        ramp(Part::Ground, Prop::A, 0, kOne, ms(tok::motion_groundDelay), ms(tok::motion_ground), Curve::Linear);
    if (kind_ == MoveKind::T4 || kind_ == MoveKind::T4m || kind_ == MoveKind::T4a) {
        pop_in(e, f);
        return;
    }
    int cd = ms(tok::motion_contentDelay), cl = ms(tok::motion_content);
    if (f.content == ContentForm::Rest) {
        step(Part::Content, Prop::A, 0, kOne, cd + cl);
        set(Part::Content, Prop::S, kOne);
    } else {
        ramp(Part::Content, Prop::S, tok::motion_contentScale, kOne, cd, cl, Curve::EaseOut);
        if (f.content == ContentForm::Step)
            step(Part::Content, Prop::A, 0, kOne, cd);
        else
            ramp(Part::Content, Prop::A, 0, kOne, cd, cl, Curve::Linear);
    }
    if (e.rows)
        rows_in(e, f);
}

void PageMove::tile_back(const MoveEnds& e)
{
    if (!e.tile_back)
        return;
    int l = ms(tok::motion_tileBack);
    ramp(Part::Tile, Prop::S, e.tile_s0, e.tile_s1, 0, l, Curve::CubicOut);
    ramp(Part::Tile, Prop::X, e.tile_x0, e.tile_x1, 0, l, Curve::CubicOut);
    ramp(Part::Tile, Prop::Y, e.tile_y0, e.tile_y1, 0, l, Curve::CubicOut);
    set(Part::Tile, Prop::A, kOne);
}

void PageMove::col_legs(const MoveEnds& e, const MoveForms& f)
{
    swap_ = ms(tok::motion_swap);
    step(Part::Leave, Prop::A, kOne, 0, 0);
    if (f.leave_fade)
        ramp(Part::LeaveHeader, Prop::A, kOne, 0, 0, ms(tok::motion_leave), Curve::Linear);
    else
        step(Part::LeaveHeader, Prop::A, kOne, 0, 0);
    step(Part::LeavePivots, Prop::A, kOne, 0, 0);
    int g = ms(tok::motion_ground);
    if (e.grounds) {
        ramp(Part::LeaveGround, Prop::A, kOne, 0, 0, g, Curve::Linear);
        ramp(Part::Ground, Prop::A, 0, kOne, 0, g, Curve::Linear);
    } else {
        set(Part::LeaveGround, Prop::A, kOne);
    }
    int word_end = std::max(ms(tok::motion_t3mLabelDelay) + ms(tok::motion_t3mLabel),
                            ms(tok::motion_t3mBaseDelay) + ms(tok::motion_t3mBase));
    if (e.word)
        word_end = word_end_ = word_legs(WordLeg::Grow, e);
    step(Part::Header, Prop::A, 0, kOne, word_end);
    tie(Part::Header);
    const Leg* s = last(Part::Word, Prop::S);
    int from = s ? (int)s->end() : ms(tok::motion_t3mLabelDelay) + ms(tok::motion_t3mLabel);
    if (f.t3m_fade)
        ramp(Part::Content, Prop::A, 0, kOne, from, ms(tok::motion_t3cFade), Curve::Linear);
    else
        step(Part::Content, Prop::A, 0, kOne, from);
    set(Part::Content, Prop::S, kOne);
}

void PageMove::art_legs(const MoveEnds& e, const MoveForms& f)
{
    swap_ = ms(tok::motion_swap);
    step(Part::Leave, Prop::A, kOne, 0, 0);
    step(Part::LeaveHeader, Prop::A, kOne, 0, 0);
    step(Part::LeavePivots, Prop::A, kOne, 0, 0);
    int g = ms(tok::motion_ground);
    if (e.grounds) {
        ramp(Part::LeaveGround, Prop::A, kOne, 0, 0, g, Curve::Linear);
        ramp(Part::Ground, Prop::A, 0, kOne, 0, g, Curve::Linear);
    } else {
        set(Part::LeaveGround, Prop::A, kOne);
    }
    int td = ms(tok::motion_t3mTileDelay), tl = ms(tok::motion_t3mTile);
    ramp(Part::Tile, Prop::S, e.tile_s0, e.tile_s1, td, tl, Curve::CubicOut);
    ramp(Part::Tile, Prop::X, e.tile_x0, e.tile_x1, td, tl, Curve::CubicOut);
    ramp(Part::Tile, Prop::Y, e.tile_y0, e.tile_y1, td, tl, Curve::CubicOut);
    set(Part::Tile, Prop::A, kOne);
    if (f.t3m_fade)
        ramp(Part::Content, Prop::A, 0, kOne, kT3aIn, kT3aLand - kT3aIn, Curve::Linear);
    else
        step(Part::Content, Prop::A, 0, kOne, kT3aIn);
    set(Part::Content, Prop::S, kOne);
    fx rest = e.tile_y1 + e.tile_s1 / 2;
    std::vector<Key> ride;
    for (int t = 0; t < kT3aLand; t += kTail) {
        fx bottom = value(Part::Tile, Prop::Y, t) + value(Part::Tile, Prop::S, t) / 2;
        ride.push_back({t, fx_mul(bottom - rest, kT3aRide)});
    }
    ride.push_back({kT3aLand, 0});
    keys(Part::Content, Prop::Y, ride.data(), (int)ride.size());
    int word_end = kT3aLand;
    if (e.word)
        word_end = word_end_ = word_legs(e.col_hold ? WordLeg::Hold : WordLeg::Grow, e);
    step(Part::Header, Prop::A, 0, kOne, word_end);
    tie(Part::Header);
}

void PageMove::zoom_legs(const MoveEnds& e, const Key* s, int ns, const Key* a, int na, fx px, int header)
{
    keys(Part::Zoom, Prop::S, s, ns);
    step(Part::Zoom, Prop::A, 0, a[0].v, a[0].t);
    for (int i = 0; i + 1 < na; i++)
        append(Leg{Part::Zoom, Prop::A, a[i].v, a[i + 1].v, a[i].t, a[i + 1].t - a[i].t, Curve::Linear});
    set(Part::Zoom, Prop::PX, fx_mul(px, e.sw));
    set(Part::Zoom, Prop::PY, e.zoom_base);
    step(Part::Header, Prop::A, 0, kOne, header);
}

void PageMove::art_back_legs(const MoveEnds& e, const MoveForms& f)
{
    int fade = ms(tok::motion_t4aFade);
    if (f.leave_fade) {
        ramp(Part::Leave, Prop::A, kOne, 0, 0, fade, Curve::Linear);
        ramp(Part::LeaveHeader, Prop::A, kOne, 0, 0, fade, Curve::Linear);
    } else {
        step(Part::Leave, Prop::A, kOne, 0, 0);
        step(Part::LeaveHeader, Prop::A, kOne, 0, 0);
    }
    set(Part::LeaveGround, Prop::A, kOne);
    tile_back(e);
    if (e.col_hold) {
        swap_ = 0;
        keys(Part::Content, Prop::A, kT4aCut, nkeys(kT4aCut));
        set(Part::Content, Prop::S, kOne);
        set(Part::Pivots, Prop::A, kOne);
        set(Part::Ground, Prop::A, kOne);
        if (e.zoom)
            zoom_legs(e, kT4aZoom, nkeys(kT4aZoom), kT4aZoomA, nkeys(kT4aZoomA), kT4aZoomX, kT4aHeader);
        else
            step(Part::Header, Prop::A, 0, kOne, kT4aHeader);
        return;
    }
    swap_ = ms(tok::motion_swap);
    if (e.word)
        word_end_ = word_legs(WordLeg::Shrink, e);
    page_in(e, f);
}

void PageMove::page_zoom(const MoveEnds& e)
{
    swap_ = ms(tok::motion_swap);
    for (Part p : {Part::Leave, Part::LeaveHeader, Part::LeavePivots})
        step(p, Prop::A, kOne, 0, kT3zCut);
    set(Part::LeaveGround, Prop::A, kOne);
    keys(Part::Content, Prop::S, kT3zScale, nkeys(kT3zScale));
    step(Part::Content, Prop::A, 0, kT3zA[0].v, kT3zA[0].t);
    for (int i = 0; i + 1 < nkeys(kT3zA); i++)
        append(Leg{Part::Content, Prop::A, kT3zA[i].v, kT3zA[i + 1].v, kT3zA[i].t, kT3zA[i + 1].t - kT3zA[i].t,
                   Curve::Linear});
    Key x[] = {{kT3zX[0].t, fx_mul(kT3zX[0].v, e.sw)}, {kT3zX[1].t, fx_mul(kT3zX[1].v, e.sw)}};
    keys(Part::Content, Prop::PX, x, 2);
    set(Part::Content, Prop::PY, fx_mul(kT3zY, e.sh));
    step(Part::Header, Prop::A, 0, kOne, kT3zLand);
    step(Part::Pivots, Prop::A, 0, kOne, kT3zLand);
    hold(Part::Header);
    hold(Part::Pivots);
    if (e.grounds)
        ramp(Part::Ground, Prop::A, 0, kOne, ms(tok::motion_groundDelay), ms(tok::motion_ground), Curve::Linear);
}

void PageMove::page_fade(const MoveEnds& e)
{
    swap_ = ms(tok::motion_swap);
    for (Part p : {Part::Leave, Part::LeaveHeader, Part::LeavePivots})
        keys(p, Prop::A, kT4zA, nkeys(kT4zA));
    ramp(Part::Leave, Prop::Y, 0, fx_mul(kT4zDrop, e.px_u), 0, kT4zDropLen, Curve::Linear);
    set(Part::LeaveGround, Prop::A, kOne);
    for (Part p : {Part::Header, Part::Pivots}) {
        step(p, Prop::A, 0, kT4zHeader0, kT4zHeader);
        append(Leg{p, Prop::A, kT4zHeader0, kOne, kT4zHeader, kT4zHeaderLand - kT4zHeader, Curve::Linear});
    }
    keys(Part::Content, Prop::A, kT4zCut, nkeys(kT4zCut));
    set(Part::Content, Prop::S, kOne);
    if (e.grounds)
        ramp(Part::Ground, Prop::A, 0, kOne, ms(tok::motion_groundDelay), ms(tok::motion_ground), Curve::Linear);
}

void PageMove::rows_out(const MoveEnds& e, bool home)
{
    if (!e.leave_rows)
        return;
    std::vector<Key> k;
    auto px = [&](const Key* ks, int n) {
        k.clear();
        for (int i = 0; i < n; i++)
            k.push_back({ks[i].t, fx_mul(ks[i].v, e.px_u)});
    };
    if (home) {
        keys(Part::Leave, Prop::S, kT4hPitch, nkeys(kT4hPitch));
        px(kT4hDx, nkeys(kT4hDx));
        keys(Part::Leave, Prop::X, k.data(), (int)k.size());
        return;
    }
    keys(Part::Leave, Prop::S, kT4RowPitch, nkeys(kT4RowPitch));
    px(kT4RowDx, nkeys(kT4RowDx));
    keys(Part::Leave, Prop::X, k.data(), (int)k.size());
    px(kT4RowDy, nkeys(kT4RowDy));
    keys(Part::Leave, Prop::Y, k.data(), (int)k.size());
}

void PageMove::row_push(const MoveEnds& e, const MoveForms& f)
{
    step(Part::Leave, Prop::A, kOne, 0, 0);
    step(Part::LeavePivots, Prop::A, kOne, 0, 0);
    if (e.lhead) {
        step(Part::LeaveHeader, Prop::A, kOne, 0, 0);
        keys(Part::LeaveWord, Prop::S, kT3OldHeader, nkeys(kT3OldHeader));
        ramp(Part::LeaveWord, Prop::Y, 0, fx_mul(kT3OldRise, e.px_u), 0, kT3OldOut, Curve::Linear);
        set(Part::LeaveWord, Prop::A, kOne);
        step(Part::LeaveWord, Prop::A, kOne, 0, kT3OldOut);
        set(Part::LeaveWord, Prop::PX, fx_mul(kT3OldHeaderX, e.sw));
        set(Part::LeaveWord, Prop::PY, e.lhead_base);
    } else if (e.morph_header_out) {
        step(Part::LeaveHeader, Prop::A, kOne, 0, 0);
    }
    if (e.grounds)
        ramp(Part::LeaveGround, Prop::A, kOne, 0, 0, f.leave_fade ? ms(tok::motion_leave) : 0, Curve::Linear);
    else
        set(Part::LeaveGround, Prop::A, kOne);
    word_end_ = word_legs(WordLeg::Push, e);
    page_in(e, f);
    step(Part::Header, Prop::A, 0, kOne, word_end_);
    tie(Part::Header);
}

void PageMove::pivot_push(const MoveEnds& e, const MoveForms& f)
{
    if (f.leave_fade) {
        keys(Part::Leave, Prop::S, kT3List, nkeys(kT3List));
        keys(Part::Leave, Prop::A, kT3ListA, nkeys(kT3ListA));
        set(Part::Leave, Prop::PX, e.tap_x);
        set(Part::Leave, Prop::PY, e.tap_y);
    } else {
        step(Part::Leave, Prop::A, kOne, 0, 0);
    }
    step(Part::LeavePivots, Prop::A, kOne, 0, 0);
    if (e.lhead) {
        step(Part::LeaveHeader, Prop::A, kOne, 0, 0);
        keys(Part::LeaveWord, Prop::S, kT3PivHeader, nkeys(kT3PivHeader));
        ramp(Part::LeaveWord, Prop::Y, 0, fx_mul(kT3PivRise, e.px_u), 0, kT3PivRiseEnd, Curve::Linear);
        keys(Part::LeaveWord, Prop::A, kT3PivHeaderA, nkeys(kT3PivHeaderA));
        set(Part::LeaveWord, Prop::PX, fx_mul(kT3PivHeaderX, e.sw));
        set(Part::LeaveWord, Prop::PY, e.lhead_base);
    } else if (e.morph_header_out) {
        step(Part::LeaveHeader, Prop::A, kOne, 0, 0);
    }
    if (e.grounds) {
        ramp(Part::LeaveGround, Prop::A, kOne, 0, 0, f.leave_fade ? ms(tok::motion_leave) : 0, Curve::Linear);
        ramp(Part::Ground, Prop::A, 0, kOne, ms(tok::motion_groundDelay), ms(tok::motion_ground), Curve::Linear);
    } else {
        set(Part::LeaveGround, Prop::A, kOne);
    }
    word_end_ = word_legs(WordLeg::Push, e);
    step(Part::Header, Prop::A, 0, kOne, word_end_);
    tie(Part::Header);
    if (e.thumb) {
        std::vector<Key> s, x, y;
        for (const Key& k : kT3Thumb) {
            s.push_back({k.t, e.tile_s0 + fx_mul(e.tile_s1 - e.tile_s0, k.v)});
            x.push_back({k.t, e.tile_x0 + fx_mul(e.tile_x1 - e.tile_x0, k.v)});
            y.push_back({k.t, e.tile_y0 + fx_mul(e.tile_y1 - e.tile_y0, k.v)});
        }
        keys(Part::Tile, Prop::S, s.data(), (int)s.size());
        keys(Part::Tile, Prop::X, x.data(), (int)x.size());
        keys(Part::Tile, Prop::Y, y.data(), (int)y.size());
        set(Part::Tile, Prop::A, kOne);
    }
    if (e.pivots) {
        keys(Part::Pivots, Prop::S, kT3PivRow, nkeys(kT3PivRow));
        step(Part::Pivots, Prop::A, 0, kT3PivRowA0, kT3PivRowIn);
        append(Leg{Part::Pivots, Prop::A, kT3PivRowA0, kOne, kT3PivRowIn, kT3PivRowLand - kT3PivRowIn, Curve::Linear});
        set(Part::Pivots, Prop::PX, fx_mul(kT3PivRowX, e.sw));
        set(Part::Pivots, Prop::PY, fx_mul(kT3PivRowY, e.sh));
    }
    step(Part::Content, Prop::A, 0, kOne, kT3PivContent);
    set(Part::Content, Prop::S, kOne);
}

void PageMove::row_pop(const MoveEnds& e, const MoveForms& f)
{
    rows_out(e, false);
    if (f.leave_fade) {
        keys(Part::Leave, Prop::A, kT4RowA, nkeys(kT4RowA));
        keys(Part::LeavePivots, Prop::A, kT4RowA, nkeys(kT4RowA));
    } else {
        step(Part::Leave, Prop::A, kOne, 0, 0);
        step(Part::LeavePivots, Prop::A, kOne, 0, 0);
    }
    step(Part::LeaveHeader, Prop::A, kOne, 0, 0);
    if (e.grounds) {
        ramp(Part::LeaveGround, Prop::A, kOne, 0, 0, ms(tok::motion_ground), Curve::Linear);
        ramp(Part::Ground, Prop::A, 0, kOne, ms(tok::motion_groundDelay), ms(tok::motion_ground), Curve::Linear);
    } else {
        set(Part::LeaveGround, Prop::A, kOne);
    }
    word_end_ = word_legs(WordLeg::Pop, e);
    if (e.zoom && f.header_zoom)
        zoom_legs(e, kT4RowZoom, nkeys(kT4RowZoom), kT4RowZoomA, nkeys(kT4RowZoomA), kT4RowZoomX, kT4RowHeader);
    else
        step(Part::Header, Prop::A, 0, kOne, kT4RowHeader);
    if (e.pivots)
        ramp(Part::Pivots, Prop::A, 0, kOne, kT4RowIn, kT4RowInLen, Curve::EaseOut);
    ramp(Part::Content, Prop::A, 0, kOne, kT4RowIn, kT4RowInLen, Curve::EaseOut);
    set(Part::Content, Prop::S, kOne);
}

void PageMove::pivot_pop(const MoveEnds& e, const MoveForms& f)
{
    if (f.leave_fade)
        keys(Part::Leave, Prop::A, kT4PivLeave, nkeys(kT4PivLeave));
    else
        step(Part::Leave, Prop::A, kOne, 0, 0);
    if (f.chrome_slide) {
        ramp(Part::LeavePivots, Prop::X, 0, fx_mul(kT4PivPivotsDx, e.px_u), 0, kT4PivPivotsMove, Curve::EaseOut);
        ramp(Part::LeavePivots, Prop::Y, 0, fx_mul(kT4PivPivotsDy, e.px_u), 0, kT4PivPivotsMove, Curve::EaseOut);
    }
    keys(Part::LeavePivots, Prop::A, kT4PivPivotsA, nkeys(kT4PivPivotsA));
    step(Part::LeaveHeader, Prop::A, kOne, 0, 0);
    if (e.grounds) {
        ramp(Part::LeaveGround, Prop::A, kOne, 0, 0, ms(tok::motion_ground), Curve::Linear);
        ramp(Part::Ground, Prop::A, 0, kOne, ms(tok::motion_groundDelay), ms(tok::motion_ground), Curve::Linear);
    } else {
        set(Part::LeaveGround, Prop::A, kOne);
    }
    word_end_ = word_legs(WordLeg::Pop, e);
    if (e.zoom && f.header_zoom) {
        zoom_legs(e, nullptr, 0, kT4PivZoomA, nkeys(kT4PivZoomA), kT4PivZoomX, kT4PivHeader);
        ramp(Part::Zoom, Prop::S, tok::motion_headerZoom, kOne, kT4PivZoomIn, kT4PivHeader - kT4PivZoomIn,
             Curve::EaseOut);
    } else {
        step(Part::Header, Prop::A, 0, kOne, kT4PivHeader);
    }
    if (e.pivots)
        keys(Part::Pivots, Prop::A, kT4PivPivots, nkeys(kT4PivPivots));
    step(Part::Content, Prop::A, 0, kT4PivRows[0].v, kT4PivRows[0].t);
    for (int i = 0; i + 1 < nkeys(kT4PivRows); i++)
        append(Leg{Part::Content, Prop::A, kT4PivRows[i].v, kT4PivRows[i + 1].v, kT4PivRows[i].t,
                   kT4PivRows[i + 1].t - kT4PivRows[i].t, Curve::Linear});
    set(Part::Content, Prop::S, kOne);
}

void PageMove::start(MoveKind k, int64_t t0, const MoveEnds& e, const MoveForms& f)
{
    kind_ = k;
    t0_ = t0;
    live_ = true;
    reversed_ = false;
    serial_++;
    clock_ = false;
    first_ = INT64_MIN;
    morph_ = f.word_morph;
    legs_.clear();
    offs_.clear();
    for (auto& row : base_)
        for (int q = 0; q < (int)Prop::Count; q++)
            row[q] = q == (int)Prop::S ? kOne : 0;
    swap_ = end_ = word_end_ = 0;
    held_ = tied_ = 0;
    int leave = ms(tok::motion_leave);
    auto leave_page = [&](int dur, bool header) {
        if (f.leave_fade) {
            ramp(Part::Leave, Prop::A, kOne, 0, 0, dur, Curve::Linear);
            if (header)
                ramp(Part::LeaveHeader, Prop::A, kOne, 0, 0, dur, Curve::Linear);
        } else {
            step(Part::Leave, Prop::A, kOne, 0, 0);
            if (header)
                step(Part::LeaveHeader, Prop::A, kOne, 0, 0);
        }
        if (e.grounds)
            ramp(Part::LeaveGround, Prop::A, kOne, 0, 0, f.leave_fade ? dur : 0, Curve::Linear);
        else
            set(Part::LeaveGround, Prop::A, kOne);
    };
    switch (k) {
    case MoveKind::T3:
    case MoveKind::T4:
    case MoveKind::T4m: {
        swap_ = ms(tok::motion_swap);
        bool row = e.morph && e.morph_kind == MorphKind::Row, pivot = e.morph && e.morph_kind == MorphKind::Pivot;
        if (k == MoveKind::T3 && row) {
            row_push(e, f);
            break;
        }
        if (k == MoveKind::T3 && pivot) {
            pivot_push(e, f);
            break;
        }
        if (k == MoveKind::T4 && row) {
            row_pop(e, f);
            break;
        }
        if (k == MoveKind::T4 && pivot) {
            pivot_pop(e, f);
            break;
        }
        leave_page(leave, true);
        if (f.leave_fade)
            ramp(Part::LeavePivots, Prop::A, kOne, 0, 0, leave, Curve::Linear);
        else
            step(Part::LeavePivots, Prop::A, kOne, 0, 0);
        if (k == MoveKind::T4m && e.word)
            word_end_ = word_legs(WordLeg::Shrink, e);
        page_in(e, f);
        break;
    }
    case MoveKind::T3c:
        col_legs(e, f);
        break;
    case MoveKind::T3a:
        art_legs(e, f);
        break;
    case MoveKind::T4a:
        art_back_legs(e, f);
        break;
    case MoveKind::T3z:
        page_zoom(e);
        break;
    case MoveKind::T4z:
        page_fade(e);
        break;
    case MoveKind::T3m: {
        swap_ = ms(tok::motion_swap);
        step(Part::Leave, Prop::A, kOne, 0, 0);
        if (f.leave_fade)
            ramp(Part::LeaveHeader, Prop::A, kOne, 0, 0, leave, Curve::Linear);
        else
            step(Part::LeaveHeader, Prop::A, kOne, 0, 0);
        step(Part::LeavePivots, Prop::A, kOne, 0, 0);
        int g = ms(tok::motion_ground);
        if (e.grounds) {
            ramp(Part::LeaveGround, Prop::A, kOne, 0, 0, g, Curve::Linear);
            ramp(Part::Ground, Prop::A, 0, kOne, 0, g, Curve::Linear);
        } else {
            set(Part::LeaveGround, Prop::A, kOne);
        }
        int td = ms(tok::motion_t3mTileDelay), tl = ms(tok::motion_t3mTile);
        ramp(Part::Tile, Prop::S, e.tile_s0, e.tile_s1, td, tl, Curve::CubicOut);
        ramp(Part::Tile, Prop::X, e.tile_x0, e.tile_x1, td, tl, Curve::CubicOut);
        ramp(Part::Tile, Prop::Y, e.tile_y0, e.tile_y1, td, tl, Curve::CubicOut);
        set(Part::Tile, Prop::A, kOne);
        int word_end = std::max(ms(tok::motion_t3mLabelDelay) + ms(tok::motion_t3mLabel),
                                ms(tok::motion_t3mBaseDelay) + ms(tok::motion_t3mBase));
        if (e.word)
            word_end = word_end_ = word_legs(WordLeg::Grow, e);
        step(Part::Header, Prop::A, 0, kOne, word_end);
        tie(Part::Header);
        int fd = ms(tok::motion_t3mFadeDelay), fl = ms(tok::motion_t3mFade);
        if (f.t3m_fade)
            ramp(Part::Content, Prop::A, 0, kOne, fd, fl, Curve::Linear);
        else
            step(Part::Content, Prop::A, 0, kOne, fd);
        set(Part::Content, Prop::S, kOne);
        break;
    }
    case MoveKind::T2:
    case MoveKind::T2n: {
        bool word = k == MoveKind::T2 && e.word;
        swap_ = ms(word ? tok::motion_t2Swap : tok::motion_swap);
        step(Part::Home, Prop::A, kOne, 0, 0);
        ramp(Part::Peek, Prop::A, e.peek0, 0, 0, ms(tok::motion_t2Peek), Curve::Linear);
        if (!word) {
            page_in(e, f);
            break;
        }
        word_end_ = word_legs(WordLeg::HomeOpen, e);
        step(Part::Header, Prop::A, 0, kOne, word_end_);
        tie(Part::Header);
        if (e.pivots) {
            int pd = ms(tok::motion_t2PivotDelay), pl = ms(tok::motion_pivotIn), pf = ms(tok::motion_pivotFade);
            if (f.chrome_slide) {
                ramp(Part::Pivots, Prop::X, e.pivot_x, 0, pd, pl, Curve::EaseOut);
                ramp(Part::Pivots, Prop::Y, e.pivot_y, 0, pd, pl, Curve::EaseOut);
                ramp(Part::Pivots, Prop::A, 0, kOne, pd, pf, Curve::Linear);
            } else {
                step(Part::Pivots, Prop::A, 0, kOne, pd + pl);
            }
        }
        if (e.rows) {
            rows_in(e, f);
            break;
        }
        ramp(Part::Ground, Prop::A, 0, kOne, ms(tok::motion_t2GroundDelay), ms(tok::motion_t2Ground), Curve::Linear);
        int cd = ms(tok::motion_t2ContentDelay), cl = ms(tok::motion_t2Content);
        int ad = ms(tok::motion_t2FadeDelay), al = ms(tok::motion_t2Fade);
        if (f.content == ContentForm::Rest) {
            step(Part::Content, Prop::A, 0, kOne, cd + cl);
        } else {
            ramp(Part::Content, Prop::S, tok::motion_contentScale, kOne, cd, cl, Curve::EaseOut);
            if (f.content == ContentForm::Step)
                step(Part::Content, Prop::A, 0, kOne, ad);
            else
                ramp(Part::Content, Prop::A, 0, kOne, ad, al, Curve::Linear);
        }
        break;
    }
    case MoveKind::T4h:
    case MoveKind::T4n: {
        bool word = k == MoveKind::T4h && e.word;
        swap_ = ms(tok::motion_t4hSwap);
        int pl = ms(tok::motion_t4hPivot), cl = ms(tok::motion_t4hContent);
        leave_page(cl, !word);
        rows_out(e, true);
        if (f.leave_fade && f.chrome_slide) {
            ramp(Part::LeavePivots, Prop::X, 0, e.pivot_x, 0, pl, Curve::EaseIn);
            ramp(Part::LeavePivots, Prop::Y, 0, e.pivot_y, 0, pl, Curve::EaseIn);
            ramp(Part::LeavePivots, Prop::A, kOne, 0, 0, pl, Curve::EaseIn);
        } else if (f.leave_fade) {
            ramp(Part::LeavePivots, Prop::A, kOne, 0, 0, pl, Curve::EaseIn);
        } else {
            step(Part::LeavePivots, Prop::A, kOne, 0, 0);
        }
        int hd = swap_, hl = ms(tok::motion_t4hItems);
        ramp(Part::Home, Prop::A, 0, kOne, hd, hl, Curve::Linear);
        ramp(Part::Peek, Prop::A, 0, e.peek1, ms(tok::motion_t4hPeekDelay), ms(tok::motion_t4hPeek), Curve::Linear);
        if (word) {
            word_end_ = word_legs(WordLeg::HomeBack, e);
            ramp(Part::Look, Prop::A, 0, kOne, hd, hl, Curve::Linear);
            tie(Part::Look);
            ramp(Part::Sub, Prop::A, 0, e.sub, ms(tok::motion_t4hSubDelay), ms(tok::motion_t4hSub), Curve::EaseOut);
        } else {
            ramp(Part::Sub, Prop::A, 0, e.sub, hd, hl, Curve::Linear);
        }
        break;
    }
    case MoveKind::T7:
    case MoveKind::T8:
        np_legs(k, e, f);
        break;
    case MoveKind::None:
        break;
    }
    if (k != MoveKind::T3m && k != MoveKind::T3a &&
        std::any_of(legs_.begin(), legs_.end(), [](const Leg& l) { return l.part == Part::Content && l.prop == Prop::A; }))
        hold(Part::Content);
    finish();
}

void PageMove::reverse(int64_t now)
{
    int64_t e = std::max<int64_t>(now - t0_, 0);
    std::vector<Leg> back;
    back.reserve(legs_.size());
    int end = 0;
    for (const Leg& l : legs_) {
        const Leg* f = leg(l.part, l.prop);
        if (f != &l)
            continue;
        const Leg* z = last(l.part, l.prop);
        Leg r = l;
        r.curve = Curve::Linear;
        r.pin = 0;
        r.from = value(l.part, l.prop, e);
        r.to = f->from;
        int64_t ran = z->end() - f->delay;
        if (ran <= 0) {
            r.delay = e >= f->delay ? (int)(e - f->delay) : 0;
            r.dur = 0;
        } else {
            r.delay = 0;
            r.dur = (int)std::clamp<int64_t>(e - f->delay, 0, ran);
        }
        end = std::max(end, (int)r.end());
        back.push_back(r);
    }
    legs_.swap(back);
    offs_.clear();
    t0_ = now;
    clock_ = true;
    swap_ = 0;
    end_ = end;
    held_ = tied_ = 0;
    reversed_ = true;
}

int64_t PageMove::frame_time(int64_t at)
{
    if (clock_)
        return at;
    if (first_ == INT64_MIN)
        first_ = at;
    return first_;
}

int64_t PageMove::shown(int64_t at)
{
    if (clock_)
        return 0;
    clock_ = true;
    int64_t d = first_ == INT64_MIN ? 0 : std::max<int64_t>(0, at - first_);
    t0_ += d;
    return d;
}

void PageMove::shift(Part p, int ms, int64_t from)
{
    if (ms <= 0)
        return;
    for (Leg& l : legs_)
        if (l.part == p && (held(p) || t0_ + l.delay >= from))
            l.delay += ms;
    finish();
}

int PageMove::release(Part p, int64_t at)
{
    if (!held(p))
        return 0;
    held_ &= ~part_bit(p);
    int64_t shown = at - t0_, r = std::max<int64_t>(shown - kTail, 0), first = INT64_MAX;
    for (const Leg& l : legs_)
        if (l.part == p && !l.pin)
            first = std::min<int64_t>(first, l.delay);
    int d = first == INT64_MAX ? 0 : (int)std::max<int64_t>(0, r - first);
    for (Leg& l : legs_) {
        if (l.part != p)
            continue;
        if (l.pin) {
            int64_t end = l.end(), s = std::max<int64_t>(l.delay, r);
            l.delay = (int)s;
            l.dur = (int)std::max<int64_t>(end - s, l.pin);
        } else {
            l.delay += d;
        }
    }
    if (p == Part::Content)
        for (int q = 0; q < (int)Prop::Count; q++) {
            Leg* z = nullptr;
            for (Leg& l : legs_)
                if (l.part == Part::Leave && l.prop == (Prop)q && (!z || l.delay >= z->delay))
                    z = &l;
            if (!z || z->end() >= shown + kTail)
                continue;
            if (z->dur <= 0)
                z->delay = (int)(shown + kTail);
            else
                z->dur = (int)(shown + kTail - z->delay);
        }
    finish();
    return d;
}

int PageMove::release(uint64_t group, int64_t at)
{
    int d = 0;
    for (int i = 0; i < (int)Part::Count; i++)
        if (group >> i & 1)
            d = std::max(d, release((Part)i, at));
    return d;
}

int PageMove::release_words(int64_t at)
{
    if (!(held_ & kWordGroup))
        return 0;
    held_ &= ~kWordGroup;
    int64_t r = std::max<int64_t>(at - t0_, 0), first = INT64_MAX;
    for (const Leg& l : legs_)
        if (kWordGroup & part_bit(l.part))
            first = std::min<int64_t>(first, l.delay);
    int d = first == INT64_MAX ? 0 : (int)std::max<int64_t>(0, r - first);
    for (Leg& l : legs_)
        if ((kWordGroup | tied_) & part_bit(l.part))
            l.delay += d;
    word_end_ += d;
    finish();
    return d;
}

void PageMove::np_pop(int delay)
{
    for (Leg& l : legs_)
        if ((kArriveGroup & part_bit(l.part)) && l.delay < delay) {
            int end = (int)l.end();
            l.delay = delay;
            l.dur = std::max(0, end - delay);
        }
    finish();
}

void PageMove::np_shift(int ms)
{
    if (ms <= 0)
        return;
    for (Leg& l : legs_)
        if (kNpInGroup & part_bit(l.part))
            l.delay += ms;
    finish();
}

}
