import { describe, expect, it } from 'vitest';
import { translate } from './translations';
describe('Netcon languages', () => {
  it('keeps English labels unchanged', () => { expect(translate('en', 'SETTINGS')).toBe('SETTINGS'); });
  it('translates Myanmar labels', () => { expect(translate('my', 'SETTINGS')).toBe('ဆက်တင်များ'); });
  it('preserves brands and configuration values', () => {
    for (const label of ['Netcon', 'YouTube 4K', 'Facebook & IG', 'PUBG Mobile', 'Instagram', 'VLESS', '1.1.1.1']) expect(translate('my', label)).toBe(label);
  });
  it('preserves Gemini spelling inside translations', () => { expect(translate('my', 'Ask Gemini')).toContain('Gemini'); });
});