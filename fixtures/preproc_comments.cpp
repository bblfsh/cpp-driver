// comment 1
#pragma once
// comment 2
#include <unordered_map>
// comment 3
#include <SFML/Graphics.hpp>
// comment 4
namespace gui
// comment 5
{
class sfml2_font : public font_i
{
public:
    sfml2_font(const sf::Font& font);
};
}

