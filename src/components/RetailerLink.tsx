import React from 'react';
import {Linking} from 'react-native';
import {platforms} from '../data/demo';
import {purchaseDestination} from '../services/purchase-links';
import {useStore} from '../store';
import {Offer, PlatformId} from '../types';
import {Button} from './ui';

export function RetailerLink({platform, offer, listingUrl, secondary, disabled}: {
  platform: PlatformId;
  offer?: Offer;
  listingUrl?: string;
  secondary?: boolean;
  disabled?: boolean;
}) {
  const {toast} = useStore();
  const name = platforms[platform].name;
  const destination = purchaseDestination(platform, offer, listingUrl);
  return <Button
    title={destination.exact ? `Buy on ${name}` : `Open ${name} website`}
    icon secondary={secondary} disabled={disabled}
    onPress={() => {
      void Linking.openURL(destination.url).catch(() => toast(`Could not open ${name}. Try again.`));
    }}
  />;
}
